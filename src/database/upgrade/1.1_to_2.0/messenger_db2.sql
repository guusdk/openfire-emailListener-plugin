# $RCSfile$
# $Revision$
# $Date$

# upgrades from Messenger 1.1.x to 2.0.x

CREATE TABLE jiveProperty (
  name        VARCHAR(100) NOT NULL,
  propValue   VARCHAR(3000) NOT NULL,
  CONSTRAINT jiveProperty_pk PRIMARY KEY (name)
);

-- MUC tables

CREATE TABLE mucRoom (
  roomID              INTEGER       NOT NULL,
  creationDate        CHAR(15)      NOT NULL,
  modificationDate    CHAR(15)      NOT NULL,
  name                VARCHAR(50)   NOT NULL,
  description         VARCHAR(255),
  canChangeSubject    INTEGER       NOT NULL,
  maxUsers            INTEGER       NOT NULL,
  publicRoom          INTEGER       NOT NULL,
  moderated           INTEGER       NOT NULL,
  invitationRequired  INTEGER       NOT NULL,
  canInvite           INTEGER       NOT NULL,
  password            VARCHAR(50)   NULL,
  canDiscoverJID      INTEGER       NOT NULL,
  logEnabled          INTEGER       NOT NULL,
  subject             VARCHAR(100)  NULL,
  rolesToBroadcast    INTEGER       NOT NULL,
  lastActiveDate      CHAR(15)      NULL,
  inMemory            INTEGER       NOT NULL,
  CONSTRAINT mucRoom_pk PRIMARY KEY (name)
);

CREATE INDEX mucRoom_roomid_idx ON mucRoom (roomID);

CREATE TABLE mucAffiliation (
  roomID              INTEGER       NOT NULL,
  jid                 VARCHAR(2000) NOT NULL,
  affiliation         INTEGER       NOT NULL,
  CONSTRAINT mucAffiliation_pk PRIMARY KEY (roomID, jid)
);

CREATE TABLE mucMember (
  roomID              INTEGER       NOT NULL,
  jid                 VARCHAR(2000) NOT NULL,
  nickname            VARCHAR(255)  NULL,
  CONSTRAINT mucMember_pk PRIMARY KEY (roomID, jid)
);

CREATE TABLE mucConversationLog (
  roomID              INTEGER       NOT NULL,
  sender              VARCHAR(2000) NOT NULL,
  nickname            VARCHAR(255)  NULL,
  time                CHAR(15)      NOT NULL,
  subject             VARCHAR(255)  NULL,
  body                LONG VARCHAR  NULL
);

# Unique ID entry for mucRoom
insert INTO jiveID (idType, id) VALUES (23, 1);