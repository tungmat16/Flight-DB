/* This file is written using Java Database Connectivity API which provides code for querying, inserting,
 * and updating relational databases. The code below creates functions used by the end user in a command-line interface
 * to interact with an database in SQL Server containing flight, reservation, and carrier information. This file is
 * only for demonstrative purposes.
 * 
 */
package flightapp;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * Runs queries against a back-end database
 */
public class Query {
  // DB Connection
  private Connection conn;

  boolean logged_in = false;
  ArrayList<Itinerary> SortedList = new ArrayList<Itinerary>();
  String logged_user = "";

  /**
   * Clear the data in any custom tables created.
   */
  public void clearTables() throws SQLException {
	  String delete_resr = "TRUNCATE TABLE Reservations";
	  String delete_user = "DELETE FROM Users";
	  String reseed = "DBCC CHECKIDENT ('Reservations', RESEED, 1)";
	  
	  Statement stm1 = conn.createStatement();
	  try {
		  stm1.executeUpdate(delete_resr);
		  stm1.executeUpdate(delete_user);
		  stm1.executeUpdate(reseed);
    } catch (Exception e) {
      System.out.println("Failed to clear tables");
    }
  }

  private void prepareStatements() throws SQLException {
    checkFlightCapacityStatement = conn.prepareStatement(CHECK_FLIGHT_CAPACITY);
    tranCountStatement = conn.prepareStatement(TRANCOUNT_SQL);
  }

  /**
   * Takes a user's username and password and attempts to log the user in.
   * If someone has already logged in, then returns "User already logged in" For all other
   * errors, returns "Login failed". Otherwise, returns "Logged in as [username]".
   */
  public String transaction_login(String username, String password) {
    String match = "SELECT username FROM Users WHERE username = ? AND password = ?";
    String username_lower = username.toLowerCase();
    
  	try {
  	  if (logged_in) {
  		  return "User already logged in\n";
  	  }
  	  PreparedStatement login = conn.prepareStatement(match);
      login.setString(1, username_lower);
        
      String salt_string = "1234";
  	  // Specify the hash parameters
  	  KeySpec spec = new PBEKeySpec(password.toCharArray(), salt_string.getBytes(), HASH_STRENGTH, KEY_LENGTH);
  	
  	  // Generate the hash
  	  SecretKeyFactory factory = null;
  	  factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
  	  byte[] password_hash = factory.generateSecret(spec).getEncoded();
  	  
  	  login.setBytes(2, password_hash);
      ResultSet rs = login.executeQuery();
      if (rs.next()) {
    	  logged_in = true;
    	  logged_user = username_lower;
    	  return "Logged in as " + username + "\n";
      }
      } catch (Exception e){
        return "Login failed\n";
      } finally {
        checkDanglingTransaction();
      }
  	return "Login failed\n";
  }

  /**
   * Implement the create user function, with specified username, password, and initial amount
   * to deposit into account.
   * Returns created username success or failure if initial deposit is less than zero or if
   * username is not unique.
   */
  public String transaction_createCustomer(String username, String password, int initAmount) {
  	if (initAmount < 0) {
  		return "Failed to create user\n";
  	}
  	String username_lower = username.toLowerCase();
  	String username_taken = "SELECT username FROM Users WHERE username = ?";
  	String create = "INSERT INTO Users(username, password, balance) Values(?, ?, ?)";
  	
    try {
    	PreparedStatement ps1 = conn.prepareStatement(username_taken);
    	PreparedStatement ps2 = conn.prepareStatement(create);
    	ps1.setString(1, username_lower);
    	ResultSet rs = ps1.executeQuery();
    	if (rs.next()) {
    		return "Failed to create user\n";
    	}
    	String salt_string = "1234";
    	byte[] salt = salt_string.getBytes();

    	// Specify the hash parameters
    	KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, HASH_STRENGTH, KEY_LENGTH);

    	// Generate the hash
    	SecretKeyFactory factory = null;
    	byte[] hash = null;
    	factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
    	hash = factory.generateSecret(spec).getEncoded();

    	ps2.setString(1, username);
    	ps2.setBytes(2, hash);
    	ps2.setInt(3, initAmount);
    	conn.setAutoCommit(false);
    	ps2.executeUpdate();
    	conn.commit();
    	conn.setAutoCommit(true);
    } catch (Exception e) {
      	try {
      		conn.rollback();
      		return "Failed to create user\n";
      	} catch (Exception e2) {
      		return "Transaction failed, rolling back database";
      	}
    } finally {
      checkDanglingTransaction();
    }
    return "Created user " + username + "\n";
  }

  class Itinerary {
	  int flight_time;
	  String info;
	  int fid1;
	  int fid2;
	  int day;
	  
	  public Itinerary(int flight_time, String info, int fid1, int fid2, int day){
		  this.flight_time = flight_time;
		  this.info = info;
		  this.fid1 = fid1;
		  this.fid2 = fid2;
		  this.day =  day;
	  }
	  
	  public String getInfo() {
		  return this.info;
	  }
	  
	  public int getFid1() {
		  return this.fid1;
	  }
	  
	  public int getFid2() {
		  return this.fid2;
	  }
	  
	  public int getDay() {
	  	return this.day;
	  }
	  
  }
  
  class SortTimes implements Comparator<Itinerary> {
	  public int compare(Itinerary a, Itinerary b) {
	  	return a.flight_time - b.flight_time;
	  }
  }
  
  /**
   * Implement the search function.
   *
   * Searches for flights from the given origin city to the given destination city, on the given day
   * of the month. If directFlight = true, it only searches for direct flights, otherwise
   * is searches for direct flights and indirect flights. Only searches for up to the number
   * of itineraries given by the argument "numberOfItineraries".
   *
   * The results are sorted based on total flight time.
   **/
 
  
  public String transaction_search(String originCity, String destinationCity, boolean directFlight,
      int dayOfMonth, int numberOfItineraries) {
	  
	  SortedList.clear();
	  StringBuffer sb = new StringBuffer();
    String direct = "SELECT TOP (?) carrier_id, flight_num, origin_city, dest_city, actual_time, capacity, price, "
      	+ "fid, day_of_month FROM Flights WHERE origin_city = ? AND dest_city = ? AND day_of_month = ? AND canceled = 0"
      	+ " ORDER BY actual_time ASC, fid ASC";
      
    try {
      PreparedStatement search = conn.prepareStatement(direct);
      search.setInt(1, numberOfItineraries);
      search.setString(2, originCity);
      search.setString(3, destinationCity);
      search.setInt(4, dayOfMonth);
      ResultSet rs1 = search.executeQuery();
        
      int itinerary = -1;
      while (rs1.next()) {
        itinerary++;
        String result_carrierId = rs1.getString(1);
        int result_flightNum = rs1.getInt(2);
        int result_time = rs1.getInt(5);
        int result_capacity = rs1.getInt(6);
        int result_price = rs1.getInt(7);
        int fid = rs1.getInt(8);
        
          
        String s = "1 flight(s), " + result_time + " minutes\nID: " + fid + " Day: " + dayOfMonth
        		+ " Carrier: " + result_carrierId + " Number: "
            + result_flightNum + " Origin: " + originCity + " Dest: "
            + destinationCity + " Duration: " + result_time + " Capacity: " + result_capacity
            + " Price: " + result_price + "\n";
        SortedList.add(new Itinerary(result_time, s, fid, -1, dayOfMonth));
      }
      rs1.close();
      if (!directFlight) {
        int remaining = numberOfItineraries - itinerary - 1;
        String indirect = "SELECT TOP (" + remaining + ") F1.fid, F1.carrier_id, F1.flight_num, F1.origin_city, F1.dest_city,"
      			+ " F1.actual_time, F1.capacity, F1.price, F2.fid, F2.carrier_id, F2.flight_num, F2.origin_city, F2.dest_city, F2.actual_time,"
      			+ " F2.capacity, F2.price, (F1.actual_time + F2.actual_time) AS total_duration FROM FLIGHTS F1 JOIN"
      			+ " FLIGHTS F2 ON F1.day_of_month = F2.day_of_month WHERE F1.origin_city = ? AND F1.dest_city = F2.origin_city AND F2.dest_city = ?"
      			+ " AND F1.canceled = 0 AND F2.canceled = 0 AND F1.day_of_month = ? ORDER BY total_duration ASC, F1.fid ASC, F2.fid ASC";
        PreparedStatement search2 = conn.prepareStatement(indirect);
        search2.setString(1, originCity);
        search2.setString(2, destinationCity);
        search2.setInt(3, dayOfMonth);
        ResultSet rs2 = search2.executeQuery();
        	
        while (rs2.next()) {
      		itinerary++;
      		int F1_fid = rs2.getInt(1);
      		String F1_carrier = rs2.getString(2);
      		int F1_flight_num = rs2.getInt(3);
      		int F1_time = rs2.getInt(6);
      		int F1_capacity = rs2.getInt(7);
      		int F1_price = rs2.getInt(8);
      		String one_stop = rs2.getString(5);
      		
      		int F2_fid = rs2.getInt(9);
      		String F2_carrier = rs2.getString(10);
      		int F2_flight_num = rs2.getInt(11);
      		int F2_time = rs2.getInt(14);
      		int F2_capacity = rs2.getInt(15);
      		int F2_price = rs2.getInt(16);
      		
      		int total_time = rs2.getInt(17);
      		
      		String s2 = "2 flight(s), " + total_time + " minutes\nID: " + F1_fid + " Day: " + dayOfMonth
          	  + " Carrier: " + F1_carrier + " Number: " + F1_flight_num + " Origin: " + originCity + " Dest: "
                + one_stop + " Duration: " + F1_time + " Capacity: " + F1_capacity + " Price: " + F1_price + "\nID: " + F2_fid + " Day: "
                + dayOfMonth + " Carrier: " + F2_carrier + " Number: " + F2_flight_num + " Origin: " + one_stop + " Dest: " + 
                destinationCity + " Duration: " + F2_time + " Capacity: " + F2_capacity + " Price: " + F2_price + "\n";
    		
      		SortedList.add(new Itinerary (total_time, s2, F1_fid, F2_fid, dayOfMonth));
      	}
      	rs2.close();
      }
      Collections.sort(SortedList, new SortTimes()); 
      if (itinerary == -1) {
        return "No flights match your selection\n";
      }  
      for (int i = 0; i < SortedList.size(); i++) {
        sb.append("Itinerary " + i + ": "+ SortedList.get(i).info);
      }
      return sb.toString();
    } catch (SQLException e) {
    	return "Failed to search\n";
    } finally {
      checkDanglingTransaction();
    }
  }

  /**
   * Implements the book itinerary function.
   *
   * itineraryId ID of the itinerary to book. The itinerary ID is returned by the search in
   * the current session.
   * 
   * User cannot book reserations without being logged in. User also cannot book an itinerary
   * without having done a search. The user cannot book two seperate itineraries on the same day.
   *//
  public String transaction_book(int itineraryId) {
  	String book = "INSERT INTO Reservations(username, paid, cancelled, fid1, fid2) VALUES(?, 0, 0, ?, ?)";
  	String rid = "SELECT rid FROM Reservations WHERE username = ? AND fid1 = ?";
  	String check_day = "SELECT F1.fid FROM Reservations R JOIN Flights F1 ON F1.fid = R.fid1 LEFT OUTER JOIN Flights F2 on R.fid1 = F2.fid" +
  			" WHERE R.username = ? AND F1.day_of_month = ?";
  	String check_capacity = "SELECT F1.fid, F1.capacity, COUNT(R.rid) FROM Flights F1 LEFT OUTER JOIN Reservations R ON F1.fid = R.fid1" +
  			" WHERE F1.day_of_month = ? AND F1.fid = ? GROUP BY F1.fid, F1.capacity";
  	String check_capacity_2nd = "SELECT F2.fid, F2.capacity, COUNT(R.rid) FROM Flights F2 LEFT OUTER JOIN Reservations R ON F2.fid = R.fid2" +
  			" WHERE F2.day_of_month = ? AND F2.fid = ? GROUP BY F2.fid, F2.capacity"; 
      
    if (!logged_in) {
      return "Cannot book reservations, not logged in\n";
    }
    
    if (SortedList.isEmpty()  || itineraryId >= SortedList.size()) {
    	return "No such itinerary " + itineraryId + "\n";
    }
    
    int day = SortedList.get(itineraryId).getDay();
    int flight_1 = SortedList.get(itineraryId).getFid1();
    int flight_2 = SortedList.get(itineraryId).getFid2();
    
  	try {
  		PreparedStatement check = conn.prepareStatement(check_day);
  		check.setString(1, logged_user);
  		check.setInt(2, day);
  		ResultSet rs_check = check.executeQuery();
  		if (rs_check.next()) {
  			rs_check.close();
  			return "You cannot book two flights in the same day\n";
  		}
  		rs_check.close();
  		// check if flight 1 has capacity
  		PreparedStatement capacity = conn.prepareStatement(check_capacity);
  		capacity.setInt(1, day);
  		capacity.setInt(2, flight_1);
  		ResultSet rs_capa = capacity.executeQuery();
  		if (rs_capa.next()) {
    		int capa_num = rs_capa.getInt(2);
    		int seats_filled = rs_capa.getInt(3);
    		rs_capa.close();
    		if ((capa_num <= seats_filled) || (capa_num == 0)) {
    			return "Booking failed\n";
    		}
  		}
  		rs_capa.close();
  		
  		// check if flight 2 has capacity
  		if (flight_2 != -1) {
  			PreparedStatement capacity2 = conn.prepareStatement(check_capacity_2nd);
  			capacity2.setInt(1, day);
  			capacity2.setInt(2, flight_2);
  			ResultSet rs_capa2 = capacity2.executeQuery();
  			if (rs_capa2.next()) {
  				int capa2 = rs_capa2.getInt(2);
  				int seats_2 = rs_capa2.getInt(3);
  				rs_capa2.close();
  				if ((capa2 <= seats_2) || (capa2 == 0)) {
  					return "Booking failed\n";
  				}
  			}
  			rs_capa2.close();
  		} 
  		
      PreparedStatement reserve = conn.prepareStatement(book);
      PreparedStatement rid_num = conn.prepareStatement(rid);
      
      reserve.setString(1, logged_user);
      reserve.setInt(2, flight_1);
      if (flight_2 == -1) {
      	reserve.setNull(3, java.sql.Types.INTEGER);
      } else {
      	reserve.setInt(3, flight_2);
      }
      conn.setAutoCommit(false);
      reserve.executeUpdate();
      //conn.commit();
      //conn.setAutoCommit(true);
      rid_num.setString(1, logged_user);
      rid_num.setInt(2, flight_1);
      ResultSet rs = rid_num.executeQuery();
      int reserved_id = -1;
      if (rs.next()) {
      	reserved_id = rs.getInt(1);
      }
      rs.close();
      conn.commit();
      conn.setAutoCommit(true);
      return "Booked flight(s), reservation ID: " + reserved_id + "\n";
    } catch (SQLException e) {
    	e.printStackTrace();
    	try {
    		conn.rollback();
    	} catch (Exception e2) {
    		return "Rollback failed";
    	}
      return "Booking failed\n";
    } finally {	
      checkDanglingTransaction();
    }
  }

  /**
   * Implements the pay function.
   */
  public String transaction_pay(int reservationId) {
    if (!logged_in) {
    	return "Cannot pay, not logged in\n";
    }
    
    boolean found = false;
    String query = "SELECT U.username, U.balance, R.rid, R.paid, R.cancelled, F1.price, F2.price" +
    		" FROM Reservations R JOIN Flights F1 ON R.fid1 = F1.fid LEFT OUTER JOIN Flights F2 ON R.fid2 = F2.fid JOIN Users U on R.username = U.username" +
    		" WHERE U.username = ? AND R.rid = ? AND R.cancelled = 0 AND R.paid = 0";
    
    String query2 = "UPDATE Reservations SET paid = 1 WHERE rid = ?";
    String query3 = "UPDATE Users SET balance = ? WHERE username = ?";
    
  	try {
    	PreparedStatement pay = conn.prepareStatement(query);
    	pay.setString(1, logged_user);
    	pay.setInt(2, reservationId);
    	ResultSet rs = pay.executeQuery();
    	int balance = 0;
    	int price1 = 0;
    	int price2 = 0;
    	if (rs.next()) {
    		found = true;
    		balance = rs.getInt(2);
    		price1 = rs.getInt(6);
    		price2 = rs.getInt(7);
    	}
    	rs.close();
    	if (!found) {
    		return "Cannot find unpaid reservation " + reservationId + " under user: " + logged_user + "\n";
    	}
    	if (price1 + price2 > balance) {
    		return "User has only " + balance + " in account but itinerary costs " + (price1 + price2) + "\n";
    	} else {
    		PreparedStatement update = conn.prepareStatement(query2);
    		update.setInt(1, reservationId);
    		conn.setAutoCommit(false);
    		update.executeUpdate();
    		PreparedStatement remain_balance = conn.prepareStatement(query3);
    		remain_balance.setInt(1, (balance - price1 - price2));
    		remain_balance.setString(2, logged_user);
    		remain_balance.executeUpdate();
    		conn.commit();
    		conn.setAutoCommit(true);
    		return "Paid reservation: " + reservationId + " remaining balance: " + (balance - price1 - price2) + "\n";
    	}
    } catch (Exception e) {
    	try {
    		conn.rollback();
    	} catch (Exception e2) {
    		return "Rollback failed";
    	}
      return "Failed to pay for reservation " + reservationId + "\n";
    } finally {
      checkDanglingTransaction();
    }
  }

  /**
   * Implements the reservations function.
   */
  public String transaction_reservations() {
  	String get_flights = "SELECT F1.fid, F1.day_of_month, F1.carrier_id, F1.flight_num, F1.origin_city, F1.dest_city, F1.actual_time, F1.capacity, F1.price," +
  			" F2.fid, F2.day_of_month, F2.carrier_id, F2.flight_num, F2.origin_city, F2.dest_city, F2.actual_time, F2.capacity, F2.price," +
  			" R.rid, R.username, R.paid, R.cancelled, R.fid1, R.fid2 " +
  			" FROM Reservations R JOIN Flights F1 ON R.fid1 = F1.fid LEFT OUTER JOIN Flights F2 ON R.fid2 = F2.fid" +
  			" WHERE R.username = ?";
  	boolean no_reservations = true;
  	StringBuffer sb = new StringBuffer();
  	
    if (!logged_in) {
    	return "Cannot view reservations, not logged in\n";
    }
  	try {
    	PreparedStatement res = conn.prepareStatement(get_flights);
  		res.setString(1, logged_user);
  		ResultSet rs2 = res.executeQuery();
  		while (rs2.next()) {
  			int cancel = rs2.getInt(22);
  			if (cancel == 0) {
    			no_reservations = false;
    			int rid = rs2.getInt(19);
    			int paid = rs2.getInt(21);
    			int fid1 = rs2.getInt(23);
    			int fid2 = rs2.getInt(24);
    			String paid_tf = "false";
    			if (paid == 1) {
    				paid_tf = "true";
    			}
    			sb.append("Reservation " + rid + " paid: " + paid_tf + ":\n");
    			int day = rs2.getInt(2);
    			String carrier1 = rs2.getString(3);
    			int num1 = rs2.getInt(4);
    			String origin1 = rs2.getString(5);
    			String dest1 = rs2.getString(6);
    			int time1 = rs2.getInt(7);
    			int capa1 = rs2.getInt(8);
    			int price1 = rs2.getInt(9);
    			sb.append("ID: " + fid1 + " Day: " + day + " Carrier: " + carrier1 + " Number: " + num1 + " Origin: " + origin1 + " Dest: "  + dest1 +
    					" Duration: " + time1 + " Capacity: " + capa1 + " Price: " + price1 +"\n");
    			if (fid2 != 0) {  // 2 flights
    				String carrier2 = rs2.getString(12);
    				int num2 = rs2.getInt(13);
    				String origin2 = rs2.getString(14);
    				String dest2 = rs2.getString(15);
    				int time2 = rs2.getInt(16);
    				int capa2 = rs2.getInt(17);
    				int price2 = rs2.getInt(18);
    				sb.append("ID: " + fid2 + " Day: " + day + " Carrier: " + carrier2 + " Number: " + num2 + " Origin: " + origin2 + " Dest: "  + dest2 +
      					" Duration: " + time2 + " Capacity: " + capa2 + " Price: " + price2 +"\n");
    			}	
  			}
  		}
  		rs2.close();
  		if (no_reservations) {
  			return "No reservations found\n";
  		}
  		return sb.toString();
  	} catch (Exception e) {
      return "Failed to retrieve reservations\n";
    } finally {
      checkDanglingTransaction();
    }
  }

  /**
   * Implements the cancel operation.
   */
  public String transaction_cancel(int reservationId) {
    if (!logged_in) {
    	return "Cannot cancel reservations, not logged in\n";
    }
    
    String query = "SELECT R.rid, U.username, R.paid, U.balance, F1.price, F2.price FROM Reservations R JOIN Users U ON R.username = U.username" +
    		" JOIN Flights F1 ON R.fid1 = F1.fid LEFT OUTER JOIN Flights F2 ON R.fid2 = F2.fid WHERE R.cancelled = 0 AND R.rid = ? AND U.username = ?";
    String query2 = "UPDATE Users SET balance = ? WHERE username = ?";
    String query3 = "UPDATE Reservations SET cancelled = 1 WHERE username = ? AND rid = ?";
    
  	try {
    PreparedStatement cancel = conn.prepareStatement(query);
    cancel.setInt(1, reservationId);
    cancel.setString(2, logged_user);
    ResultSet rs = cancel.executeQuery();
    int paid = -1;
    int balance = 0;
    int price1 = 0;
    int price2 = 0;
    if (rs.next()) {
    	paid = rs.getInt(3);
    	balance = rs.getInt(4);
    	price1 = rs.getInt(5);
    	price2 = rs.getInt(6);
    }
    rs.close();
    PreparedStatement cnc = conn.prepareStatement(query3);
    conn.setAutoCommit(false);
    if (paid == 1) {
    	PreparedStatement refund = conn.prepareStatement(query2);
    	refund.setInt(1, (balance + price1 + price2));
    	refund.setString(2, logged_user);
    	refund.executeUpdate();
    }
    if (paid != -1) {
    	cnc.setString(1, logged_user);
    	cnc.setInt(2, reservationId);
    	cnc.executeUpdate();
    	conn.commit();
    	conn.setAutoCommit(true);
    	return "Canceled reservation " + reservationId + "\n";
    } else {
    	conn.setAutoCommit(true);
    	return "Failed to cancel reservation " + reservationId + "\n"; 
    }
    } catch (Exception e) {
    	try {
    		conn.rollback();
    	} catch (Exception e2) {
    		return "Failed to rollback transaction";
    	}
      return "Failed to cancel reservation " + reservationId + "\n";
    } finally {
      checkDanglingTransaction();
    }
  }

  /**
   * A class to store flight information.
   */
  class Flight {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    public String toString() {
      return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: "
          + flightNum + " Origin: " + originCity + " Dest: " + destCity + " Duration: " + time
          + " Capacity: " + capacity + " Price: " + price;
    }
  }
}
