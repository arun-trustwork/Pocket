-- update_fts.sql
DROP TRIGGER IF EXISTS contacts_ai;
DROP TRIGGER IF EXISTS contacts_ad;
DROP TRIGGER IF EXISTS contacts_au;
DROP TABLE IF EXISTS contacts_fts;

CREATE VIRTUAL TABLE contacts_fts USING fts5(
    name,
    company,
    address,
    raw_json,
    content='contacts',
    content_rowid='rowid'
);

CREATE TRIGGER contacts_ai AFTER INSERT ON contacts BEGIN
  INSERT INTO contacts_fts(rowid, name, company, address, raw_json)
  VALUES (new.rowid, new.name, new.company, new.address, new.raw_json);
END;

CREATE TRIGGER contacts_ad AFTER DELETE ON contacts BEGIN
  INSERT INTO contacts_fts(contacts_fts, rowid, name, company, address, raw_json)
  VALUES('delete', old.rowid, old.name, old.company, old.address, old.raw_json);
END;

CREATE TRIGGER contacts_au AFTER UPDATE ON contacts BEGIN
  INSERT INTO contacts_fts(contacts_fts, rowid, name, company, address, raw_json)
  VALUES('delete', old.rowid, old.name, old.company, old.address, old.raw_json);
  INSERT INTO contacts_fts(rowid, name, company, address, raw_json)
  VALUES (new.rowid, new.name, new.company, new.address, new.raw_json);
END;

INSERT INTO contacts_fts(contacts_fts) VALUES('rebuild');
