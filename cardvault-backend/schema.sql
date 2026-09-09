-- schema.sql
PRAGMA foreign_keys = OFF;
DROP TABLE IF EXISTS business_contacts;
DROP TABLE IF EXISTS project_contacts;
DROP TABLE IF EXISTS projects;
DROP TABLE IF EXISTS businesses;
DROP TABLE IF EXISTS contact_tags;
DROP TABLE IF EXISTS tags;
DROP TABLE IF EXISTS contacts_fts;
DROP TABLE IF EXISTS contacts;
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS contacts (
    id TEXT PRIMARY KEY,
    name TEXT,
    company TEXT,
    designation TEXT,
    phone TEXT,
    email TEXT,
    website TEXT,
    address TEXT,
    type TEXT CHECK(type IN ('vendor', 'consumer', 'both', 'none')) DEFAULT 'none',
    raw_json TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tags (
    id TEXT PRIMARY KEY,
    name TEXT UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS contact_tags (
    contact_id TEXT,
    tag_id TEXT,
    PRIMARY KEY (contact_id, tag_id),
    FOREIGN KEY (contact_id) REFERENCES contacts(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS businesses (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    vertical_label TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS business_contacts (
    business_id TEXT,
    contact_id TEXT,
    role TEXT,
    PRIMARY KEY (business_id, contact_id),
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
    FOREIGN KEY (contact_id) REFERENCES contacts(id) ON DELETE CASCADE
);

-- Full-Text Search Virtual Table
CREATE VIRTUAL TABLE IF NOT EXISTS contacts_fts USING fts5(
    name,
    company,
    address,
    tags,
    content='contacts',
    content_rowid='rowid'
);

-- Triggers to keep FTS table in sync
-- Insert trigger
CREATE TRIGGER IF NOT EXISTS contacts_ai AFTER INSERT ON contacts BEGIN
  INSERT INTO contacts_fts(rowid, name, company, address, tags)
  VALUES (new.rowid, new.name, new.company, new.address, '');
END;

-- Delete trigger
CREATE TRIGGER IF NOT EXISTS contacts_ad AFTER DELETE ON contacts BEGIN
  INSERT INTO contacts_fts(contacts_fts, rowid, name, company, address, tags)
  VALUES('delete', old.rowid, old.name, old.company, old.address, '');
END;

-- Update trigger
CREATE TRIGGER IF NOT EXISTS contacts_au AFTER UPDATE ON contacts BEGIN
  INSERT INTO contacts_fts(contacts_fts, rowid, name, company, address, tags)
  VALUES('delete', old.rowid, old.name, old.company, old.address, '');
  INSERT INTO contacts_fts(rowid, name, company, address, tags)
  VALUES (new.rowid, new.name, new.company, new.address, '');
END;
