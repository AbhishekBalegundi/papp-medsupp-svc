-- One extra object for the picker-ordering test, kept out of the shared fixture so no other
-- test's counts move. It sorts first alphabetically and contains "m" without beginning with it,
-- which is the case that used to bury the real matches: a search for "m" filled the page with
-- names like this one and left "Monthly Premium Summary" on a later page.
INSERT INTO TM_Reports (RPT_Name, RPT_Style, RPT_Folder) VALUES
    ('AAA Member List', 'Report', 'Daily');
