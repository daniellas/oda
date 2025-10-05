import pyspark.sql.functions as f
from empi_environments.env_qa import *
from empi_pyspark_utils.spark_support.context import create_local_context

from functions import read_ts, read_jira_issues, normalize_jira_issues, \
    read_gitlab_projects, read_gitlab_mrs, calculate_mrs_duration, count_mrs_comments, \
    read_gitlab_commits, calculate_commits_stats, past_date

spark = create_local_context(cassandra_host=CASSANDRA_HOST, cpus=2, driver_memory='512M')

# TS
ts = read_ts(spark).cache()
ts_jira_hours = ts.where(f.col('jira_id').isNotNull())
ts_other_hours = ts.where(f.col('jira_id').isNull())
users_jira_hours = ts_jira_hours.groupBy('month', 'user').agg(f.sum('hours').alias('jira_hours'))
users_other_hours = ts_other_hours.groupBy('month', 'user').agg(f.sum('hours').alias('other_hours'))
users_hours = users_jira_hours \
    .join(users_other_hours, ['month', 'user'], 'left') \
    .withColumn('total_hours', f.col('jira_hours') + f.col('other_hours')) \
    .withColumn('other_hours_pct', f.col('other_hours') / f.col('total_hours') * 100) \
    .orderBy(f.col('month').desc(), f.col('user'))

# JIRA
cryp_issues = read_jira_issues(spark, 'CRYP')
qt_issues = read_jira_issues(spark, 'QT')
de_issues = read_jira_issues(spark, 'DE')
trad_issues = read_jira_issues(spark, 'TRAD')

issues_normalized = normalize_jira_issues(cryp_issues, True) \
    .union(normalize_jira_issues(qt_issues, False)) \
    .union(normalize_jira_issues(de_issues, False)) \
    .union(normalize_jira_issues(trad_issues, False)) \
    .cache()
issues_open = issues_normalized.where(f.col('resolutiondate').isNull()).orderBy(f.col('created').desc())
issues_completed = issues_normalized.where(f.col('resolutiondate').isNotNull()).orderBy(f.col('created').desc())
issues_completed_by_month = issues_completed.groupBy('project', 'resolutiondate_month', 'type').count()
issues_hours = ts.groupBy('jira_id').agg(f.sum('hours').alias('issue_total_hours')).withColumnRenamed('jira_id', 'key')

# Gitlab projects
projects = read_gitlab_projects(spark).cache()

# Gitlab merge requests
mrs = read_gitlab_mrs(spark).cache()
mrs_count = mrs.groupBy('month').agg(f.count('*').alias('count')).orderBy(f.col('month').desc())
project_mrs = projects.join(mrs, projects.id == mrs.project_id)
mrs_count_by_type = project_mrs.groupBy('month', 'project_type').agg(f.count('*').alias('count')).orderBy(
    f.col('month').desc())
mrs_duration = calculate_mrs_duration(project_mrs)
mrs_comments = count_mrs_comments(project_mrs)

# Gitlab commits
commits = read_gitlab_commits(spark).cache()
project_commits = commits.join(projects, commits.projectId == projects.id)
commits_by_project_type = calculate_commits_stats(project_commits, commits)


def user_issues(user, past_days=180):
    return issues_normalized \
        .join(ts, ts.jira_id == issues_normalized.key, 'left') \
        .join(issues_hours, issues_hours.key == issues_normalized.key) \
        .where(f.col('user') == user) \
        .where(f.col('status_changed_at') > past_date(past_days)) \
        .groupBy(
        issues_normalized.key,
        f.col('summary'),
        f.col('status'),
        f.col('type'),
        f.col('priority'),
        f.col('size'),
        f.col('size_points'),
        f.col('status_changed_at'),
        f.col('issue_total_hours')) \
        .agg(f.sum('hours').alias('user_total_hours')) \
        .orderBy(f.col('status_changed_at').desc())


def user_other_hours(user):
    return ts_other_hours \
        .where(f.col('user') == user) \
        .groupBy('month', 'task_desc') \
        .agg(f.sum('hours').alias('other_hours')) \
        .orderBy(f.col('month').desc())


def user_issues_hours_monthly(user):
    return ts \
        .where(f.col('user') == user) \
        .groupBy('month', 'jira_id', 'task_desc') \
        .agg(f.sum('hours').alias('hours')) \
        .join(issues_normalized, ts.jira_id == issues_normalized.key) \
        .select('month', 'jira_id', 'summary', 'status', 'hours') \
        .orderBy(f.col('month').desc(), f.col('jira_id'))


def user_issues_points_monthly(user):
    return issues_normalized \
        .join(ts, ts.jira_id == issues_normalized.key, 'left') \
        .where(f.col('user') == user) \
        .where(f.col('resolutiondate_month').isNotNull()) \
        .select('resolutiondate_month', 'key', 'size_points') \
        .distinct() \
        .groupBy('resolutiondate_month') \
        .agg(f.sum('size_points').alias('points')) \
        .orderBy('resolutiondate_month')


def user_issues_hours_weekly(user, past_days=180):
    return ts \
        .where(f.col('user') == user) \
        .where(f.col('day') >= past_date(past_days)) \
        .groupBy('week', 'jira_id', 'task_desc') \
        .agg(f.sum('hours').alias('hours')) \
        .join(issues_normalized, ts.jira_id == issues_normalized.key) \
        .select('week', 'jira_id', 'summary', 'status', 'hours') \
        .orderBy(f.col('week').desc(), f.col('jira_id'))


def user_mrs_duration(user):
    return project_mrs \
        .where(f.col('author.username') == user) \
        .where(f.col('project_type') == 'code') \
        .groupBy('month') \
        .agg(
        f.avg('duration_days').alias('duration_days_avg'),
        f.min('duration_days').alias('duration_days_min'),
        f.max('duration_days').alias('duration_days_max'),
        f.expr('percentile(duration_days, array(0.25,0.5,0.75,0.9,0.99))').alias('duration_days_pct')) \
        .withColumn('duration_days_pct25', f.col('duration_days_pct')[0]) \
        .withColumn('duration_days_pct50', f.col('duration_days_pct')[1]) \
        .withColumn('duration_days_pct75', f.col('duration_days_pct')[2]) \
        .withColumn('duration_days_pct90', f.col('duration_days_pct')[3]) \
        .withColumn('duration_days_pct99', f.col('duration_days_pct')[4])


def user_code_commits(user):
    return project_commits \
        .where(f.col('committer') == user) \
        .where(f.col('project_type') == 'code') \
        .groupBy('month') \
        .agg(
        f.count(commits.id).alias('count'),
        f.sum('additions').alias('additions'),
        f.sum('deletions').alias('deletions')) \
        .orderBy(f.col('month').desc())
