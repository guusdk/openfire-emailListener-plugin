# $RCSfile$
# $Revision$
# $Date$

CREATE TABLE jiveUser (
  username              VARCHAR(32)     NOT NULL,
  password              VARCHAR(32)     NOT NULL,
  name                  VARCHAR(100),
  email                 VARCHAR(100),
  creationDate          CHAR(15)        NOT NULL,
  modificationDate      CHAR(15)        NOT NULL,
  PRIMARY KEY (username),
  INDEX jiveUser_cDate_idx (creationDate)
);

CREATE TABLE jiveUserProp (
  username              VARCHAR(32)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             TEXT            NOT NULL,
  PRIMARY KEY (username, name)
);

CREATE TABLE jiveGroup (
  groupID               BIGINT          NOT NULL,
  name                  VARCHAR(50)     NOT NULL,
  description           VARCHAR(255),
  creationDate          CHAR(15)        NOT NULL,
  modificationDate      CHAR(15)        NOT NULL,
  PRIMARY KEY (groupID),
  INDEX jiveGroup_cDate_idx (creationDate),
  INDEX jiveGroup_name_idx (name(10))
);

CREATE TABLE jiveGroupProp (
  groupID               BIGINT          NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             TEXT            NOT NULL,
  PRIMARY KEY (groupID, name)
);

CREATE TABLE jiveGroupUser (
  groupID               BIGINT          NOT NULL,
  userID                BIGINT          NOT NULL,
  administrator         TINYINT         NOT NULL,
  PRIMARY KEY (groupID, userID, administrator)
);

CREATE TABLE jivePrivate (
  username              VARCHAR(32)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  namespace             VARCHAR(200)    NOT NULL,
  value                 TEXT            NOT NULL,
  PRIMARY KEY (username, name, namespace)
);

CREATE TABLE jiveOffline (
  username              VARCHAR(32)     NOT NULL,
  messageID             BIGINT          NOT NULL,
  creationDate          CHAR(15)        NOT NULL,
  messageSize           INTEGER         NOT NULL,
  message               TEXT            NOT NULL,
  PRIMARY KEY (username, messageID)
);

CREATE TABLE jiveRoster (
  rosterID              BIGINT          NOT NULL,
  username              VARCHAR(32)     NOT NULL,
  jid                   TEXT            NOT NULL,
  sub                   TINYINT         NOT NULL,
  ask                   TINYINT         NOT NULL,
  recv                  TINYINT         NOT NULL,
  nick                  VARCHAR(255),
  PRIMARY KEY (rosterID),
  INDEX jiveRoster_unameid_idx (username)
);

CREATE TABLE jiveRosterGroups (
  rosterID              BIGINT          NOT NULL,
  rank                  TINYINT         NOT NULL,
  groupName             VARCHAR(255)    NOT NULL,
  PRIMARY KEY (rosterID, rank),
  INDEX jiveRosterGroup_rosterid_idx (rosterID)
);

CREATE TABLE jiveVCard (
  username              VARCHAR(32)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             TEXT            NOT NULL,
  PRIMARY KEY (username, name)
);

CREATE TABLE jiveID (
  idType                INTEGER         NOT NULL,
  id                    BIGINT          NOT NULL,
  PRIMARY KEY (idType)
);

CREATE TABLE jiveProperty (
  name        VARCHAR(100)              NOT NULL,
  propValue   TEXT                      NOT NULL,
  PRIMARY KEY (name)
);

# MUC Tables

CREATE TABLE mucRoom (
  roomID              BIGINT        NOT NULL,
  creationDate        CHAR(15)      NOT NULL,
  modificationDate    CHAR(15)      NOT NULL,
  name                VARCHAR(50)   NOT NULL,
  naturalName         VARCHAR(255)  NOT NULL,
  description         VARCHAR(255),
  canChangeSubject    TINYINT       NOT NULL,
  maxUsers            INTEGER       NOT NULL,
  publicRoom          TINYINT       NOT NULL,
  moderated           TINYINT       NOT NULL,
  invitationRequired  TINYINT       NOT NULL,
  canInvite           TINYINT       NOT NULL,
  password            VARCHAR(50)   NULL,
  canDiscoverJID      TINYINT       NOT NULL,
  logEnabled          TINYINT       NOT NULL,
  subject             VARCHAR(100)  NULL,
  rolesToBroadcast    TINYINT       NOT NULL,
  lastActiveDate      CHAR(15)      NULL,
  inMemory            TINYINT       NOT NULL,
  PRIMARY KEY (name),
  INDEX mucRoom_roomid_idx (roomID)
);

CREATE TABLE mucAffiliation (
  roomID              BIGINT        NOT NULL,
  jid                 TEXT          NOT NULL,
  affiliation         TINYINT       NOT NULL,
  PRIMARY KEY (roomID,jid(70))
);

CREATE TABLE mucMember (
  roomID              BIGINT        NOT NULL,
  jid                 TEXT          NOT NULL,
  nickname            VARCHAR(255)  NULL,
  PRIMARY KEY (roomID,jid(70))
);

CREATE TABLE mucConversationLog (
  roomID              BIGINT        NOT NULL,
  sender              TEXT          NOT NULL,
  nickname            VARCHAR(255)  NULL,
  time                CHAR(15)      NOT NULL,
  subject             VARCHAR(255)  NULL,
  body                TEXT          NULL
);

# Finally, insert default table values.

# Unique ID entry for user, group
# The User ID entry starts at 2 (after admin user entry).
INSERT INTO jiveID (idType, id) VALUES (3, 2);
INSERT INTO jiveID (idType, id) VALUES (4, 1);
INSERT INTO jiveID (idType, id) VALUES (18, 1);
INSERT INTO jiveID (idType, id) VALUES (19, 1);
INSERT INTO jiveID (idType, id) VALUES (23, 1);

# Create User  Permissions Table
CREATE TABLE jiveUserPerm (
  objectType            INTEGER         NOT NULL,
  objectID              BIGINT          NOT NULL,
  username              VARCHAR(32)     NOT NULL,
  permission            INTEGER         NOT NULL,
  INDEX jiveUserPerm_object_idx (objectType, objectID),
  INDEX jiveUserPerm_uname_idx (username)
);

# Entry for admin user
INSERT INTO jiveUser (username, password, name, email, creationDate, modificationDate)
    VALUES ('admin', 'admin', 'Administrator', 'admin@example.com', '0', '0');