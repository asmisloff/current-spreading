CREATE TABLE "asu_ter_m_electrical_schema"
(
    "id"           bigint                              NOT NULL GENERATED BY DEFAULT AS IDENTITY,
    "active"       boolean                             NOT NULL,
    "change_time"  timestamp default CURRENT_TIMESTAMP NOT NULL,
    "name"         varchar(70)                         NOT NULL,
    "description"  varchar(150)                        NOT NULL,
    "type"         varchar(50)                         NOT NULL,
    "length"       varchar(50)                         NOT NULL,
    "coordinates"  varchar(50)                         NOT NULL,
    "branch_count" int                                 NOT NULL,
    "track_count"  int                                 NOT NULL,
    "main_schema"  jsonb                               NOT NULL,
    "branches"     jsonb                               NOT NULL,
    CONSTRAINT "PK_asu_ter_m_electrical_schema" PRIMARY KEY ("id")
);