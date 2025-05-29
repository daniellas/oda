from pyspark.sql.functions import udf
import pyspark.sql.functions as f
from pyspark.sql.types import IntegerType, DoubleType, TimestampType

ROMAN_MONTHS = {
    'I': 1,
    'II': 2,
    'III': 3,
    'IV': 4,
    'V': 5,
    'VI': 6,
    'VII': 7,
    'VIII': 8,
    'IX': 9,
    'X': 10,
    'XI': 11,
    'XII': 12
}
TS_MONTH_DAYS = [d for d in range(1, 31)]
POINT_HOURS = 8

day_expr = 'stack(31'

for day in TS_MONTH_DAYS:
    day_expr = day_expr + f',{day},`{day}`'

day_expr = day_expr + ') as (day,hours)'

def duration_days(start, end):
    return f.round((f.unix_timestamp(end) - f.unix_timestamp(start)) / 86400)


def read_ts(spark):
    return spark.read.options(header=True)\
        .csv('../data/timesheets/*.csv')\
        .withColumn('month', month_number(f.col('Month')))\
        .selectExpr(
            'Year as year',
            'month',
            day_expr,
            'Who as user',
            'Project as project',
            '`Jira-id` as jira_id',
            '`Task (description internal)` as task_desc',
            'Category as category')\
        .withColumn('year',f.col('year').cast(IntegerType()))\
        .withColumn('month',f.col('month').cast(IntegerType()))\
        .withColumn('hours',f.col('hours').cast(DoubleType()))\
        .withColumn('day',f.to_timestamp(f.concat_ws('-', 'year', 'month', 'day'), 'yyyy-M-d'))\
        .withColumn('month',f.date_trunc('month',f.col('day')))\
        .drop('year')\
        .where(f.col('hours').isNotNull())\
        .where(f.col('hours') > 0)\
        .where(f.col('project') != 'PaidAbsence')\
        .orderBy(f.col('year').desc(),f.col('month').desc(),f.col('day').desc())
  

@udf
def month_number(month):
    if month in ROMAN_MONTHS.keys():
        return ROMAN_MONTHS[month]


@udf
def size_points(size):
    if size == 'XS':
        return 1
    elif size == 'S':
        return 2
    elif size == 'M':
        return 5
    elif size == 'L':
        return 18
    elif size == 'XL':
        return 34

    return None


@udf
def current_status(changelog):
    status_changes = []

    for hist in sorted(changelog.histories, key=lambda i: i.id):
        for item in hist.items:
            if item.fieldId == 'status':
                status_changes.append(item.toStr)

    if len(status_changes) > 0:
        return status_changes[-1]


@udf
def current_status_changed_at(changelog):
    status_changes = []

    for hist in sorted(changelog.histories, key=lambda i: i.id):
        for item in hist.items:
            if item.fieldId == 'status':
                status_changes.append(hist.created)

    if len(status_changes) > 0:
        return status_changes[-1]

def read_jira_issues(spark):
    return spark.read.json('../data/jira-issues-CRYP.json')

def normalize_jira_issues(issues):
    return issues\
        .withColumn('status',current_status(f.col('changelog')))\
        .withColumn('status_changed_at',f.to_timestamp(current_status_changed_at(f.col('changelog'))))\
        .select(
            f.col('key'),
            f.col('fields.summary'),
            f.col('status'),
            f.col('status_changed_at'),
            f.col('fields.created'),
            f.col('fields.resolutiondate'),
            f.col('fields.issuetype.name').alias('type'),
            f.col('fields.priority.name').alias('priority'),
            f.col('fields.customfield_10035.value').alias('size'))\
        .where(f.col('type').isin(['Bug','Story','Sub-task']))\
        .withColumn('created',f.col('created').cast(TimestampType()))\
        .withColumn('resolutiondate',f.col('resolutiondate').cast(TimestampType()))\
        .withColumn('created_month',f.date_trunc('month','created'))\
        .withColumn('resolutiondate_month',f.date_trunc('month','resolutiondate'))\
        .withColumn('size_points',size_points(f.col('size')))\
        .withColumn('size_hours',f.col('size_points') * POINT_HOURS)


def filter_heavy_open_issues(issues_open, ts, issues_hours, work_hours_threshold, skip_status):
    return issues_open\
        .join(ts,ts.jira_id == issues_open.key)\
        .join(issues_hours, issues_hours.key == issues_open.key)\
        .groupBy(
            issues_open.key,
            f.col('summary'),
            f.col('type'),
            f.col('priority'),
            f.col('status'),
            f.col('size'),
            f.col('user'),
            f.col('issue_total_hours'))\
        .agg(f.sum('hours').alias('user_total_hours'))\
        .where(f.col('user_total_hours') > work_hours_threshold)\
        .where(~f.col('status').isin(skip_status))\
        .orderBy(f.col('user_total_hours').desc())

@udf
def project_type(path):
    if path.endswith('-dag'):
        return 'dag'
    elif path.endswith('-pipeline'):
        return 'pipeline'
    elif 'feed-features' in path or path.endswith('assembly'):
        return 'assembly'
    elif (path.startswith('empirica-algo/installers') and path.endswith('-installer')) or path.endswith(
            'infrastructure-installer'):
        return 'installer'

    return 'code'


def read_gitlab_projects(spark):
    return spark.read.json('../data/gitlab-projects.json')\
        .withColumn('name_with_namespace',f.regexp_replace('name_with_namespace', ' ', ''))\
        .withColumn('project_type',project_type(f.col('name_with_namespace')))\
        .drop('tag_list')


def read_gitlab_mrs(spark):
    return spark.read.json('../data/gitlab-merge-requests.json')\
        .where(f.col('state')=='merged')\
        .withColumn('created_at',f.col('created_at').cast(TimestampType()))\
        .withColumn('updated_at',f.col('updated_at').cast(TimestampType()))\
        .withColumn('closed_at',f.col('closed_at').cast(TimestampType()))\
        .withColumn('merged_at',f.col('merged_at').cast(TimestampType()))\
        .withColumn('month',f.date_trunc('month',f.col('merged_at')))\
        .withColumn('duration_days',duration_days('created_at','merged_at'))\
        .withColumn('jira_id',f.concat(f.lit('CRYP-'),f.regexp_extract('source_branch', r'(\d+)', 1)))


def calculate_mrs_duration(project_mrs):
    return project_mrs\
        .groupBy('month','project_type')\
        .agg(
            f.avg('duration_days').alias('duration_days_avg'),
            f.min('duration_days').alias('duration_days_min'),
            f.max('duration_days').alias('duration_days_max'),
            f.expr('percentile(duration_days, array(0.25,0.5,0.75,0.9,0.99))').alias('duration_days_pct'))\
        .withColumn('duration_days_pct25',f.col('duration_days_pct')[0])\
        .withColumn('duration_days_pct50',f.col('duration_days_pct')[1])\
        .withColumn('duration_days_pct75',f.col('duration_days_pct')[2])\
        .withColumn('duration_days_pct90',f.col('duration_days_pct')[3])\
        .withColumn('duration_days_pct99',f.col('duration_days_pct')[4])


def count_mrs_comments(project_mrs):
    return project_mrs\
        .groupBy('month','project_type')\
        .agg(
            f.count('*').alias('count'),
            f.sum('user_notes_count').alias('comments'))\
        .withColumn('comments_avg',f.col('comments')/f.col('count'))


def read_gitlab_commits(spark):
    return spark.read.json('../data/commits/*.json')\
        .withColumn('committer',f.split('committer','@')[0])\
        .withColumn('createdAt',f.col('createdAt').cast(TimestampType()))\
        .withColumn('month',f.date_trunc('month','createdAt'))


def calculate_commits_stats(project_commits):
    return project_commits\
        .groupBy('month','project_type')\
        .agg(
            f.count(commits.id).alias('count'),
            f.sum('additions').alias('additions'),
            f.sum('deletions').alias('deletions'))

