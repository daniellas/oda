import os
from csv import writer
from pathlib import Path

from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build

DATA_DIR = '../data/timesheets'
SECRETS_DIR = f'{Path.home()}/.teams-performance-analyser'
GOOGLE_TOKEN_PATH = f'{SECRETS_DIR}/token.json'
GOOGLE_CREDENTIALS_PATH = f'{SECRETS_DIR}/credentials.json'
GOOGLE_API_SCOPES = ['https://www.googleapis.com/auth/spreadsheets.readonly']

TIMESHEETS = [
    ('DLa', 2024, '1DHy_fJc22qe0E2UDFMxzbeuRkb5SVPalEOdGB8eunmk'),
    ('DLa', 2025, '1BLuVTo90n2xvnmyNSOAxsr1bbOQDZsIOLNmjw7bpGzM'),
    ('KBa', 2024, '185Q9Mf39ZbgAL6Ql1lo33SiUzBQd5DUQ2I_5gxHioi4'),
    ('KBa', 2025, '1fXZTcMX8sZwrjzjTvEH_q6_2LZWlrk8bDjTws-_k0kU'),
    ('PAn', 2024, '1-EvpP08ZWmILcKD-gmVFNsjjgls1Y_yOOIOrVJaAlXU'),
    ('PAn', 2025, '1KIeB_-r9HO_x4J0HBwAdhFx4_AK2uRXINR0FQVJ1bCY'),
    ('TGa', 2024, '1aRu7zfScL1QmujguYwi1YHR9Mm2MDeV9bvEctGewDMI'),
    ('TGa', 2025, '1RfURQtqfBLJQ4r4xjdzcbPiWlo7VL5xZtWNDTiWBcaQ'),
    ('MRy', 2024, '1pSdkpRK3JB9WGhcCiJ6cTPi7uFOXSdrtMWS2gpYj_gA'),
    ('MRy', 2025, '17xkRS0IKtF4kY_VrpBYnxGs--kz_0DxZScNoBt9TV3c'),
    ('PGr', 2024, '15xcCW7VIZcopl6HRaR7lhB4YA3rWXxuP1MabDttdiaY'),
    ('PGr', 2025, '1F9hNQVmnqcoQmFffGKz87uYq5XGn0WZG5egmqRXsmBY'),
    ('PKo', 2024, '1gepFGMAiOPx5L-JvcJJu-4daNR-nLI8f5MzsnFaFbMA'),
    ('PKo', 2025, '150VoPpkeY7A_afFzg3GQEzroXHG08ge0u9PKZ-WvJKI'),
    ('MSo', 2024, '1AEww7_vq_OKS6NlNHV0Tuevn8YCdME6iEA6DCvAerfs'),
    ('MSo', 2025, '1YSxq60sOUjsZyWsDrj8lcH-wE61GeHnvorRKJJU6468'),
    ('ATa', 2024, '1L81d5QnBU9jniJHSkbL_TEjG1UPgRJKGytDyYgwpd1Q'),
    ('ATa', 2025, '1iRB2PNdihh--AjltV2W0LfvSRHCw2OGIPTb1toilyhk'),
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
