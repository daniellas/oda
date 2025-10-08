import os
from csv import writer
from pathlib import Path

from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build

DATA_DIR = '../data/timesheets/ipbox'
SECRETS_DIR = f'{Path.home()}/.teams-performance-analyser'
GOOGLE_TOKEN_PATH = f'{SECRETS_DIR}/token.json'
GOOGLE_CREDENTIALS_PATH = f'{SECRETS_DIR}/credentials.json'
GOOGLE_API_SCOPES = ['https://www.googleapis.com/auth/spreadsheets.readonly']

TIMESHEETS = [
    ('DLa', 2019, '1UTj4WQNaryduLld71aii-AYJPkbCivWqjzD4CEaQl4I'),
    ('DLa', 2020, '17gERBmWUhE2F0pZ7KPL-WhugjNAD5ZePUPW3U3KgiGY'),
    ('DLa', 2021, '1VwhNK8LuVMAZpam71ayWTeI9NeKfmqnX3CimxWw-dBM')
]


def read_credentials(token_path, creds_path, scopes):
    creds = None

    if os.path.exists(token_path):
        creds = Credentials.from_authorized_user_file(token_path, scopes)
    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            flow = InstalledAppFlow.from_client_secrets_file(creds_path, scopes)
            creds = flow.run_local_server(port=0)
        with open(token_path, 'w') as token:
            token.write(creds.to_json())

    return creds


def fetch_timesheet(creds, spreadsheet_id, range):
    return build('sheets', 'v4', credentials=creds) \
        .spreadsheets() \
        .values() \
        .get(spreadsheetId=spreadsheet_id, range=range) \
        .execute() \
        .get('values', [])


def main():
    if not os.path.exists(DATA_DIR):
        os.mkdir(DATA_DIR)

    creds = read_credentials(GOOGLE_TOKEN_PATH, GOOGLE_CREDENTIALS_PATH, GOOGLE_API_SCOPES)

    for user, year, ts_id in TIMESHEETS:
        print(f'Fetching {user} year {year} timesheet')
        ts = fetch_timesheet(creds, ts_id, f'TimeSheet_{year}')

        with open(f'{DATA_DIR}/{user}_{year}.csv', 'w') as file:
            writer(file).writerows(ts[1:])


if __name__ == '__main__':
    main()
