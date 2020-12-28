/* This file creates the Flights Database schema that is queried and updated using Java
Database Connectivity API as demonstrated in Flight DB Java.java
*/
IF NOT EXISTS (
SELECT TABLE_NAME
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_NAME = 'Carriers')
BEGIN
    CREATE TABLE Carriers(cid varchar(7) primary key, name varchar(83) not null)
END

IF NOT EXISTS (
SELECT TABLE_NAME
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_NAME = 'Months')
BEGIN
    CREATE TABLE Months(mid int primary key, month varchar(20) not null)
END

IF NOT EXISTS (
SELECT TABLE_NAME
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_NAME = 'Weekdays')
BEGIN
	CREATE TABLE Weekdays(did int primary key, day_of_week varchar(20) not null)
END

IF NOT EXISTS (
SELECT TABLE_NAME
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_NAME = 'Flights')
BEGIN
	CREATE TABLE Flights(
	fid int primary key,
	month_id int not null references Months,
	day_of_month int not null,
	day_of_week_id int not null references Weekdays,
	carrier_id varchar(7) not null references Carriers,
	flight_num int not null,
	origin_city varchar(34) not null,
	origin_state varchar(47) not null,
	dest_city varchar(34) not null,
	dest_state varchar(46) not null,
	departure_delay int not null,
	taxi_out int not null,
	arrival_delay int not null,
	canceled int not null,
	actual_time int not null,
	distance int not null,
	capacity int not null,
	price int not null)
END

IF NOT EXISTS (
SELECT TABLE_NAME
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_NAME = 'Users')
BEGIN
    CREATE TABLE Users(
	username varchar(20) PRIMARY KEY,
	[password] varbinary(30) NOT NULL,
	balance INT)
END

IF NOT EXISTS (
SELECT TABLE_NAME
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_NAME = 'Reservations')
BEGIN
	CREATE TABLE Reservations(
	rid INT IDENTITY(1,1) PRIMARY KEY,
	username varchar(20) FOREIGN KEY REFERENCES Users(username) NOT NULL,
	paid INT NOT NULL,
	cancelled INT NOT NULL,
	fid1 INT FOREIGN KEY REFERENCES Flights(fid) NOT NULL,
	fid2 INT FOREIGN KEY REFERENCES Flights(fid))
END;
