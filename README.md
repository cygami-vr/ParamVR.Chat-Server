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

# Makes the column case insensitive
alter table user change name name varchar(16)
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
    unique key (user_id, name),
    unique key (user_id, vrc_uuid),
    foreign key (user_id) references user(id) on delete cascade
);

create table parameter(
    id bigint primary key auto_increment,
    user_id bigint,
    avatar_id bigint,
    type tinyint,
    description varchar(64),
    name varchar(32),
    `key` varchar(16),
    data_type tinyint,
    default_value varchar(16),
    min_value varchar(16),
    max_value varchar(16),
    saved char(1) default 'Y' not null,
    `order` tinyint,
    unique key (user_id, avatar_id, name),
    foreign key (user_id) references user(id) on delete cascade,
    foreign key (avatar_id) references avatar(id) on delete restrict
);

create table parameter_value(
    parameter_id bigint not null,
    description varchar(64),
    value varchar(16),
    `key` varchar(16),
    primary key (parameter_id, value),
    foreign key (parameter_id) references parameter(id) on delete cascade
);
```