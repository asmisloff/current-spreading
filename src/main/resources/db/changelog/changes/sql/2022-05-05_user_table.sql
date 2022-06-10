CREATE TABLE "asu_ter_u_user"
(
    "id"                        bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY,
    "login"                     varchar(30) NOT NULL,
    "password"                  varchar(128) NOT NULL,
    "last_name"                 varchar(50) NOT NULL,
    "first_name"                varchar(50) NOT NULL,
    "middle_name"               varchar(50) NOT NULL,
    "organisation"              varchar(500) NOT NULL,
    CONSTRAINT "PK_user" PRIMARY KEY ( "id" ),
    CONSTRAINT "AK1_asu_ter_u_user" UNIQUE ( "login" )
);