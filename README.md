## DDL

```sql
create table user(
    id bigint primary key auto_increment,
    name varchar(16) unique key,
    failed_logins tinyint(4),
    salt binary(32),
    salted_hash binary(32),
    listen_key varchar(36)
);

create table user_settings(
    user_id bigint primary key,
    avatar_change_cooldown smallint default 60,
    color_primary char(6),
    color_secondary char(6),
    dark_mode_color_primary char(6),
    dark_mode_color_secondary char(6),
    foreign key (user_id) references user(id) on delete cascade
);

# Makes the column case insensitive
alter table user modify name varchar(16)
character set latin1 collate latin1_general_ci null default null;

create table quick_auth(
    user_id bigint not null,
    auth_key varchar(36) primary key,
    date_created timestamp default current_timestamp,
    foreign key (user_id) references user(id) on delete cascade
);

create table avatar(
    id bigint primary key auto_increment,
    user_id bigint,
    vrc_uuid varchar(41) not null,
    name varchar(64),
    allow_change char(1) default 'N',
    change_requires_invite char(1) default 'N',
    title varchar(64),
    unique key (user_id, name),
    unique key (user_id, vrc_uuid),
    foreign key (user_id) references user(id) on delete cascade
);

-- Consider splitting into separate tables by type
-- LOV default_value
-- Toggle default_value
-- Slider min_value, max_value, default_value
-- Button press_value, release_value, min_press_time, max_press_time

create table parameter(
    id bigint primary key auto_increment,
    user_id bigint,
    avatar_id bigint,
    type tinyint,
    description varchar(64),
    name varchar(32),
    requires_invite char(1) default 'N' not null,
    data_type tinyint,
    default_value varchar(16),
    min_value varchar(16),
    max_value varchar(16),
    saved char(1) default 'Y' not null,
    lockable char(1) default 'N' not null,
    press_value varchar(16),
    `order` tinyint,
    unique key (user_id, avatar_id, name),
    foreign key (user_id) references user(id) on delete cascade,
    foreign key (avatar_id) references avatar(id) on delete restrict
);

create table invite(
    id bigint primary key auto_increment,
    url char(8),
    user_id bigint not null,
    expires bigint,
    unique key (url),
    foreign key (user_id) references user(id) on delete cascade
);

alter table invite modify url char(8) character set latin1
 collate latin1_general_cs null default null;

create table invite_permission(
    invite_id bigint,
    parameter_id bigint,
    foreign key (invite_id) references invite(id) on delete cascade,
    foreign key (parameter_id) references parameter(id) on delete cascade
);

create table invite_avatar_change(
    invite_id bigint,
    avatar_id bigint,
    foreign key (invite_id) references invite(id) on delete cascade,
    foreign key (avatar_id) references avatar(id) on delete cascade
);

create table parameter_value(
    parameter_id bigint not null,
    description varchar(64),
    value varchar(16),
    requires_invite char(1) default 'N' not null,
    primary key (parameter_id, value),
    foreign key (parameter_id) references parameter(id) on delete cascade
);

create table locked_parameter(
    parameter_id bigint primary key,
    client_id varchar(36),
    invite_id bigint,
    foreign key (parameter_id) references parameter(id) on delete cascade,
    foreign key (invite_id) references invite(id) on delete cascade
);

create table trigger_session(
    uuid varchar(36) primary key,
    client_id varchar(36),
    target_user varchar(16) not null,
    invite_id bigint,
    foreign key (invite_id) references invite(id) on delete cascade
);

```