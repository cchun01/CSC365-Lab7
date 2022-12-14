import java.sql.*;
import java.sql.Date;
import java.time.temporal.ChronoUnit;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

public class InnReservations {
    public static void main(String[] args) {
        System.out.println(System.getenv());
        try {
            InnReservations ir = new InnReservations();
            switch (Integer.parseInt(args[0])) {
                case 1:
                    ir.roomsAndRates();
                    break;
                case 2:
                    ir.reservation();
                    break;
                case 3:
                    ir.reservationChange();
                    break;
                case 4:
                    ir.reservationCancellation();
                    break;
                case 5:
                    ir.reservationInformation();
                    break;
                case 6:
                    ir.revenue();
                    break;
            }
        } catch (SQLException e) {
            System.err.println("SQLException: " + e.getMessage());
        } catch (Exception e2) {
            System.err.println("Exception: " + e2.getMessage());
        }
    }

    // When this option is selected, the system shall output a list of rooms
    // to the user sorted by popularity
    // •Room popularity score: number of days the room has been occupied during the
    // previous
    // 180 days divided by 180 (round to two decimal places)
    // •Next available check-in date.
    // •Length in days and check out date of the most recent (completed) stay in the
    // room.
    private void roomsAndRates() throws SQLException {

        System.out.println("FR1: Rooms and Rates\r\n");

        try (Connection conn = DriverManager.getConnection(System.getenv("HP_JDBC_URL"),
                System.getenv("HP_JDBC_USER"),
                System.getenv("HP_JDBC_PW"))) {

            String roomsAndRates = "with Popular as ( SELECT Room, ROUND((SUM(DateDiff(least(Checkout, CURRENT_DATE), greatest(CheckIn,  DATE_SUB(CURRENT_DATE, INTERVAL 180 day)))))/180, 2) as Popularity FROM hp_reservations WHERE CheckOut >= (DATE_SUB(CURRENT_DATE, INTERVAL 180 day)) GROUP BY Room), mostRecent as ( SELECT hp_rooms.RoomName as RoomName, hp_reservations.Room as Room, MAX(Checkout) as recentCheckout, MAX(CheckIn) as recentCheckIn FROM hp_reservations INNER JOIN hp_rooms ON hp_reservations.Room = hp_rooms.RoomCode GROUP By hp_reservations.Room, hp_rooms.RoomName) SELECT RoomName, mostRecent.Room, Popularity, DATE_ADD(recentCheckout, INTERVAL 1 day), DATEDIFF(recentCheckout, recentCheckIn) FROM mostRecent INNER JOIN Popular ON Popular.Room = mostRecent.Room ORDER BY Popularity";

            try (Statement stmt = conn.createStatement()) {
                ResultSet result = stmt.executeQuery(roomsAndRates);
                System.out.printf("%-27s%-10s%-12s%-25s%-22s\n", "RoomName", "RoomCode", "Popularity",
                        "Next Avalible CheckIn", "Recent Stay Length");
                while (result.next()) {
                    String roomName = result.getString(1);
                    String roomCode = result.getString(2);
                    String popularity = result.getString(3);
                    String nextCheckIn = result.getString(4);
                    String recentStayLength = result.getString(5);
                    System.out.printf("%-27s%-10s%-12s%-25s%-22s\n", roomName, roomCode, popularity,
                            nextCheckIn, recentStayLength);
                }
            }
        }
    }

    private Double getCost(LocalDate checkIn, LocalDate checkOut, double baseprice) {
        Double cost = 0.00;
        for (LocalDate date = checkIn; date.isBefore(checkOut); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                cost += (baseprice * 1.1);
            } else {
                cost += baseprice;
            }
        }
        return (double) Math.round(cost * 100) / 100;
    }

    private void reservation() throws SQLException {
        System.out.println("FR2: Reservation\r\n");
        try (Connection conn = DriverManager.getConnection(System.getenv("HP_JDBC_URL"),
                System.getenv("HP_JDBC_USER"),
                System.getenv("HP_JDBC_PW"))) {

            Scanner scanner = new Scanner(System.in);

            System.out.println("Enter your first name: ");
            String firstName = scanner.nextLine();

            System.out.println("Enter your last name: ");
            String lastName = scanner.nextLine();

            System.out.println("Enter a room code  ('Any' for no preference): ");
            String roomCode = scanner.nextLine();

            System.out.println("Enter bed type ('Any' for no preference): ");
            String bedType = scanner.nextLine();

            System.out.println("Enter check-in date (YYYY-MM-DD): ");
            String checkIn = scanner.nextLine();

            System.out.println("Enter check-out date (YYYY-MM-DD): ");
            String checkOut = scanner.nextLine();

            System.out.println("Enter number of children: ");
            String kids = scanner.nextLine();

            System.out.println("Enter number of adults: ");
            String adults = scanner.nextLine();

            int totalPeople = Integer.parseInt(kids) + Integer.parseInt(adults);

            LocalDate ld = LocalDate.parse(checkOut);
            LocalDate monthLater = ld.plusMonths(1);
            String laterDate = monthLater.toString();

            conn.setAutoCommit(false);

            int maxOcc = 4;
            String[][] suggested_rooms = new String[5][];

            if (maxOcc >= totalPeople && checkOut.compareTo(checkIn) != -1) {
                String reservationQuery = new StringBuilder()
                        .append("WITH occupied as ( \n")
                        .append("SELECT RoomCode FROM hp_reservations JOIN hp_rooms ON Room = RoomCode ")
                        .append("WHERE (Checkout > ? and Checkout <= ?) ")
                        .append("OR (CheckIn >= ? and CheckIn < ?)) \n")
                        .append("SELECT * FROM ( ")
                        .append("SELECT RoomCode, RoomName, maxOcc, Beds, bedType, decor, basePrice, ")
                        .append("1 as SuggestedOrder, ? as  SuggestedCheckIn, ")
                        .append("? as SuggesetedCheckOut, ")
                        .append("(CASE RoomCode IN (SELECT * FROM occupied) ")
                        .append("WHEN True THEN 'UnAvailable' ")
                        .append("ElSE 'Available' ")
                        .append("END) as 'status' FROM hp_rooms JOIN hp_reservations ON Room = RoomCode ")
                        .append("WHERE RoomCode LIKE ? AND BedType LIKE ? ")
                        .append("GROUP BY RoomCode HAVING status = 'Available' \n")
                        .append("UNION \n ")
                        .append("SELECT RoomCode, RoomName, maxOcc, Beds, bedType, decor, basePrice, ")
                        .append("2 as SuggestedOrder, MAX(CheckOut) as SuggestedCheckIn, DATE_ADD(Max(CheckOut), ")
                        .append("INTERVAL DATEDIFF(?, ?) DAY) as SuggestedCheckOut, ")
                        .append("'Available' as status FROM hp_rooms JOIN hp_reservations ON Room = RoomCode ")
                        .append("GROUP BY RoomCode ")
                        .append("HAVING (MAX(CheckOut) LIKE ? OR MAX(CheckOut) LIKE ?)) ")
                        .append("as combinedRooms WHERE maxOcc >= ? \n ORDER BY SuggestedOrder LIMIT 5;").toString();

                try (PreparedStatement pstmt = conn.prepareStatement(reservationQuery)) {

                    // WITH STATEMENT
                    pstmt.setDate(1, java.sql.Date.valueOf(checkIn));
                    pstmt.setDate(2, java.sql.Date.valueOf(checkOut));
                    pstmt.setDate(3, java.sql.Date.valueOf(checkIn));
                    pstmt.setDate(4, java.sql.Date.valueOf(checkOut));
                    // checkin / checkout dates for thet next statement and roomCode/bedtype
                    pstmt.setDate(5, java.sql.Date.valueOf(checkIn));
                    pstmt.setDate(6, java.sql.Date.valueOf(checkOut));

                    if (roomCode.equalsIgnoreCase("any"))
                        pstmt.setString(7, "%");
                    else
                        pstmt.setString(7, "%" + roomCode + "%");

                    if (bedType.equalsIgnoreCase("any"))
                        pstmt.setString(8, "%");
                    else
                        pstmt.setString(8, "%" + bedType + "%");

                    // select after union
                    pstmt.setDate(9, java.sql.Date.valueOf(checkOut));
                    pstmt.setDate(10, java.sql.Date.valueOf(checkIn));

                    pstmt.setString(11, checkOut.substring(0, checkOut.length() - 2) + '%');

                    pstmt.setString(12, laterDate.substring(0, laterDate.length() - 2) + '%');

                    pstmt.setInt(13, totalPeople);
                    ResultSet result = pstmt.executeQuery();
                    int index = 1;
                    System.out.println("\n\n\n\n\n");

                    System.out.printf("%-7s%-10s%-27s%-20s%-10s%-15s%-15s%-15s%-15s%-15s%-15s\n", "Index", "RoomCode",
                            "RoomName", "Max Occupancy",
                            "Beds", "Bed Type", "Decor", "Base Price", "Check In", "Check Out", "Total Cost ($)");

                    while (result.next()) {
                        String RoomCode = result.getString(1);
                        String RoomName = result.getString(2);
                        String occ = result.getString(3);
                        String Beds = result.getString(4);
                        String type = result.getString(5);
                        String decor = result.getString(6);
                        String basePrice = result.getString(7);
                        String SuggestedCheckIn = result.getString(9);
                        String SuggestedCheckOut = result.getString(10);
                        Double cost = getCost(LocalDate.parse(SuggestedCheckIn), LocalDate.parse(SuggestedCheckOut),
                                Double.parseDouble(basePrice));

                        String[] temp = { RoomCode, RoomName, occ, Beds, type, decor, basePrice, SuggestedCheckIn,
                                SuggestedCheckOut, cost.toString() };
                        suggested_rooms[index - 1] = temp;
                        System.out.printf("%-7s%-10s%-27s%-20s%-10s%-15s%-15s%-15s%-15s%-15s%-15s\n", index, RoomCode,
                                RoomName, occ,
                                Beds, type, decor, basePrice, SuggestedCheckIn, SuggestedCheckOut, "$" + cost);
                        index++;
                    }
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                }

                System.out.print("\n\nEnter a number 1-5 to book that room. " + "To cancel request, enter cancel : ");
                String res = scanner.nextLine();

                if (res.equalsIgnoreCase("cancel")) {
                    System.out.println("Request has been cancelled");
                } else {
                    int index = Integer.parseInt(res);
                    System.out.println(Arrays.toString(suggested_rooms[index - 1]));
                    System.out.println("Reservations Details:");
                    System.out.printf("%-25s%-15s%-25s%-15s%-15s%-15s%-10s%-8s%-15s\n", "Name", "RoomCode", "RoomName",
                            "Bed Type", "Check In", "Check Out", "Adults", "Kids", "Total Cost ($)");
                    System.out.printf("%-25s%-15s%-25s%-15s%-15s%-15s%-10s%-8s%-15s\n", firstName + " " + lastName,
                            suggested_rooms[index - 1][0], suggested_rooms[index - 1][1], suggested_rooms[index - 1][4],
                            suggested_rooms[index - 1][7], suggested_rooms[index - 1][8], adults, kids,
                            suggested_rooms[index - 1][9]);

                    System.out.println("Type confirm to book reservation. To cancel request, type cancel : ");
                    String response = scanner.nextLine();
                    if (response.equalsIgnoreCase("cancel")) {
                        System.out.println("Request has been cancelled");
                        return;
                    } else {
                        // get reservation code
                        int resCode = 0;
                        String codequery = new StringBuilder().append("SELECT MAX(Code) FROM hp_reservations")
                                .toString();
                        try (PreparedStatement pstmt = conn.prepareStatement(codequery)) {
                            ResultSet result = pstmt.executeQuery();
                            while (result.next()) {
                                String code = result.getString(1);
                                resCode = Integer.parseInt(code) + 1;
                            }
                        }
                        System.out.println("Your reservation code: " + resCode);

                        // insert into table
                        String insertquery = new StringBuilder()
                                .append("INSERT INTO hp_reservations (CODE, Room, CheckIn, Checkout, Rate, LastName, FirstName, Adults, Kids) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                                .toString();
                        try (PreparedStatement pstmt = conn.prepareStatement(insertquery)) {
                            double rate = Double.parseDouble(suggested_rooms[index - 1][9])
                                    / ChronoUnit.DAYS.between(LocalDate.parse(suggested_rooms[index - 1][7]),
                                            LocalDate.parse(suggested_rooms[index - 1][8]));
                            double roundedRate = Math.round(rate * 100.0) / 100.0;
                            pstmt.setInt(1, resCode);
                            pstmt.setString(2, suggested_rooms[index - 1][0]);
                            pstmt.setDate(3, java.sql.Date.valueOf(suggested_rooms[index - 1][7]));
                            pstmt.setDate(4, java.sql.Date.valueOf(suggested_rooms[index - 1][8]));
                            pstmt.setDouble(5, roundedRate);
                            pstmt.setString(6, lastName);
                            pstmt.setString(7, firstName);
                            pstmt.setString(8, adults);
                            pstmt.setString(9, kids);

                            int rowCount = pstmt.executeUpdate();
                            System.out.format("Updated %d records for reservation%n", rowCount);

                            conn.commit();
                        } catch (SQLException e) {
                            conn.rollback();
                        }
                        System.out.println("Reservation complete");
                    }

                }
            } else {
                System.out.println("There are no rooms available for your requested capacity");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void reservationChange() throws SQLException {
        System.out.println("FR3: Reservation Change\r\n");
        try (Connection conn = DriverManager.getConnection(System.getenv("HP_JDBC_URL"),
                System.getenv("HP_JDBC_USER"),
                System.getenv("HP_JDBC_PW"))) {

            Scanner scanner = new Scanner(System.in);
            System.out.println("Enter a reservation code: ");
            String code = scanner.nextLine();
            System.out.println("For each prompt, enter a new value or 'no change'");
            System.out.println("Enter a new first name: ");
            String firstName = scanner.nextLine();
            System.out.println("Enter a new last name: ");
            String lastName = scanner.nextLine();
            System.out.println("Enter a new check-in date (YYYY-MM-DD): ");
            String checkIn = scanner.nextLine();
            System.out.println("Enter a new check-out date (YYYY-MM-DD): ");
            String checkOut = scanner.nextLine();
            System.out.println("Enter a new number of children: ");
            String kids = scanner.nextLine();
            System.out.println("Enter a new number of adults: ");
            String adults = scanner.nextLine();

            // Checks if the checkout date comes before the check-in date
            if (checkOut.compareTo(checkIn) == -1) {
                System.out.println("Check out date cannot be before check in");
                return;
            }

            // If the check-in or check-out dates have been updated, this is to check if
            // they conflict
            // with any other reservations in the same room
            if (!"no change".equalsIgnoreCase(checkIn) || !"no change".equalsIgnoreCase(checkOut)) {
                StringBuilder getRoom = new StringBuilder("SELECT * FROM hp_reservations WHERE CODE = ?");
                String room;
                try (PreparedStatement pstmt = conn.prepareStatement(getRoom.toString())) {
                    pstmt.setObject(1, Integer.parseInt(code));
                    try (ResultSet rs = pstmt.executeQuery()) {
                        rs.next();
                        room = rs.getString(2);
                    }
                }

                StringBuilder dateChecker = new StringBuilder(
                        "SELECT * FROM hp_reservations WHERE room = ? AND CheckIn < ? AND Checkout > ? ");
                String changedDate;
                if (!"no change".equalsIgnoreCase(checkIn)) {
                    changedDate = checkIn;
                } else {
                    changedDate = checkOut;
                }
                try (PreparedStatement pstmt = conn.prepareStatement(dateChecker.toString())) {
                    pstmt.setObject(1, room);
                    pstmt.setObject(2, changedDate);
                    pstmt.setObject(3, changedDate);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.first() == true) {
                            System.out.println(
                                    "The new check-in or check-out date conflicts with another reservation in the same room");
                            return;
                        }
                    }
                }
            }

            // The program reaches here if there was no time conflict or updated check-in or
            // check-out dates
            List<Object> params = new ArrayList<Object>();
            int first = 0;
            StringBuilder sb = new StringBuilder("UPDATE hp_reservations SET");
            if (!"no change".equalsIgnoreCase(firstName)) {
                sb.append(" FirstName = ?");
                params.add(firstName);
                first += 1;
            }
            if (!"no change".equalsIgnoreCase(lastName)) {
                if (first == 0) {
                    sb.append(" LastName = ?");
                    params.add(lastName);
                    first += 1;
                } else {
                    sb.append(", LastName = ?");
                    params.add(lastName);
                }
            }
            if (!"no change".equalsIgnoreCase(checkIn)) {
                if (first == 0) {
                    sb.append(" CheckIn = ?");
                    params.add(LocalDate.parse(checkIn));
                    first += 1;
                } else {
                    sb.append(", CheckIn = ?");
                    params.add(LocalDate.parse(checkIn));
                }
            }
            if (!"no change".equalsIgnoreCase(checkOut)) {
                if (first == 0) {
                    sb.append(" Checkout = ?");
                    params.add(LocalDate.parse(checkOut));
                    first += 1;
                } else {
                    sb.append(", Checkout = ?");
                    params.add(LocalDate.parse(checkOut));
                }
            }
            if (!"no change".equalsIgnoreCase(kids)) {
                if (first == 0) {
                    sb.append(" Kids = ?");
                    params.add(Integer.parseInt(kids));
                    first += 1;
                } else {
                    sb.append(", Kids = ?");
                    params.add(Integer.parseInt(kids));
                }
            }
            if (!"no change".equalsIgnoreCase(adults)) {
                if (first == 0) {
                    sb.append(" Adults = ?");
                } else {
                    sb.append(", Adults = ?");
                }
                params.add(Integer.parseInt(adults));
            }
            sb.append(" WHERE CODE = ?");
            params.add(Integer.parseInt(code));

            try (PreparedStatement pstmt = conn.prepareStatement(sb.toString())) {
                int i = 1;
                for (Object p : params) {
                    pstmt.setObject(i++, p);
                }
                int rowCount = pstmt.executeUpdate();
                System.out.format("Updated %d records for reservation %s%n", rowCount, code);

            } catch (SQLException e) {
                conn.rollback();
            }
        }
    }

    private void reservationCancellation() throws SQLException {
        System.out.println("FR4: Reservation Cancellation\r\n");
        try (Connection conn = DriverManager.getConnection(System.getenv("HP_JDBC_URL"),
                System.getenv("HP_JDBC_USER"),
                System.getenv("HP_JDBC_PW"))) {

            Scanner scanner = new Scanner(System.in);
            System.out.println("Enter a reservation code: ");
            String code = scanner.nextLine();

            StringBuilder delete = new StringBuilder("DELETE FROM hp_reservations WHERE CODE = ?");

            try (PreparedStatement pstmt = conn.prepareStatement(delete.toString())) {
                pstmt.setObject(1, Integer.parseInt(code));
                int rowCount = pstmt.executeUpdate();
                System.out.format("Updated %d records for reservation %s%n", rowCount, code);
            }
        }
    }

    private void reservationInformation() throws SQLException {
        System.out.println("FR5: Detailed Reservation Information\r\n");
        try (Connection conn = DriverManager.getConnection(System.getenv("HP_JDBC_URL"),
                System.getenv("HP_JDBC_USER"),
                System.getenv("HP_JDBC_PW"))) {

            Scanner scanner = new Scanner(System.in);
            System.out.println("Enter any combination of the fields listed below. A blank entry should indicate Any");
            // First name
            System.out.println("Enter a first name: ");
            String First = scanner.nextLine();
            if (First.equals("")) {
                First = "\'%%\'";
            } else {
                First = "\'" + First + "\'";
            }
            // Last name
            System.out.println("Enter a last name: ");
            String Last = scanner.nextLine();
            if (Last.equals("")) {
                Last = "\'%%\'";
            } else {
                Last = "\'" + Last + "\'";
            }
            // A range of dates
            String dateString = "";
            System.out.println("Enter a range of dates(xxxx-x-x : xxxx-x-x)  ");
            String dateInput = scanner.nextLine();
            if (dateInput != "") {
                String[] dates = (dateInput).split(":");
                System.out.print(dates);
                dateString += " AND Checkin = \'" + dates[0] + "\' AND CheckOut = \'" + dates[1] + "\'";
            }
            // Room code
            System.out.println("Enter a room code: ");
            String RoomCode = scanner.nextLine();
            if (RoomCode.equals("")) {
                RoomCode = "\'%%\'";
            } else {
                RoomCode = "\'" + RoomCode + "\'";
            }
            // Reservation code
            System.out.println("Enter a reservation code: ");
            String ReservationCode = scanner.nextLine();
            if (ReservationCode.equals("")) {
                ReservationCode = "\'%%\'";
            } else {
                ReservationCode = "\'" + ReservationCode + "\'";
            }

            String reservationInfo = "SELECT CODE, Room, RoomName, Checkin, Checkout, Rate, LastName, FirstName, Adults, Kids FROM hp_reservations INNER JOIN hp_rooms on hp_reservations.Room = hp_rooms.RoomCode WHERE Room LIKE "
                    + RoomCode + " AND CODE LIKE " + ReservationCode + " AND FirstName LIKE " + First
                    + " AND LastName LIKE "
                    + Last + dateString;

            try (Statement stmt = conn.createStatement()) {
                ResultSet result = stmt.executeQuery(reservationInfo);
                System.out.printf("%-10s%-10s%-22s%-15s%-15s%-10s%-12s%-12s%-10s%-10s\n", "CODE", "RoomCode",
                        "RoomName",
                        "CheckIn", "Checkout", "Rate", "LastName", "FirstName", "Adults", "Kids");
                while (result.next()) {
                    String CODE = result.getString(1);
                    String Room = result.getString(2);
                    String RoomName = result.getString(3);
                    String CheckIn = result.getString(4);
                    String Checkout = result.getString(5);
                    String Rate = result.getString(6);
                    String LastName = result.getString(7);
                    String FirstName = result.getString(8);
                    String Adults = result.getString(9);
                    String Kids = result.getString(10);
                    System.out.printf("%-10s%-10s%-22s%-15s%-15s%-10s%-12s%-12s%-10s%-10s\n", CODE, Room, RoomName,
                            CheckIn, Checkout, Rate, LastName, FirstName, Adults, Kids);
                }
            }
        }
    }

    private void revenue() throws SQLException {

        System.out.printf(
                "-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------%n");
        System.out.printf(
                "                                                                               Month by Month Revenue This Year                                                                                %n");
        System.out.printf(
                "-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------%n");
        System.out.printf(
                "| %-30s | %-9s | %-9s | %-9s | %-9s | %-9s | %-9s | %-9s | %-9s | %-9s | %-9s | %-9s | %-9s | %-10s |%n",
                "Room", "January", "February", "March", "April", "May",
                "June", "July", "August", "September", "October", "November",
                "December", "Total");
        System.out.printf(
                "-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------%n");

        try (Connection conn = DriverManager.getConnection(System.getenv("HP_JDBC_URL"),
                System.getenv("HP_JDBC_USER"),
                System.getenv("HP_JDBC_PW"))) {

            String roomsSql = "SELECT distinct RoomName FROM hp_rooms ORDER BY RoomName";

            try (Statement roomsStmt = conn.createStatement();
                    ResultSet roomsResult = roomsStmt.executeQuery(roomsSql)) {
                while (roomsResult.next()) {
                    String roomName = roomsResult.getString("RoomName");

                    float[] mRev = new float[12];
                    float yRev = 0;

                    String mRevSql = String.format(
                            "SELECT MONTH(CheckOut) as month, SUM(ROUND(DATEDIFF(checkout, checkin) * rate, 2)) as mRevenue FROM hp_reservations join hp_rooms on Room = RoomCode where YEAR(CheckOut) = YEAR(CURRENT_DATE()) and RoomName = '%s' group by month",
                            roomName);
                    String yRevSql = String.format(
                            "SELECT SUM(ROUND(DATEDIFF(checkout, checkin) * rate, 2)) as yRevenue FROM hp_reservations join hp_rooms on Room = RoomCode where YEAR(CheckOut) = YEAR(CURRENT_DATE()) and RoomName = '%s'",
                            roomName);

                    try (Statement mRevStmt = conn.createStatement();
                            ResultSet mRevResult = mRevStmt.executeQuery(mRevSql)) {
                        while (mRevResult.next()) {
                            int month = mRevResult.getInt("month") - 1;
                            mRev[month] = mRevResult.getFloat("mRevenue");
                        }
                    }

                    try (Statement yRevStmt = conn.createStatement();
                            ResultSet yRevResult = yRevStmt.executeQuery(yRevSql)) {
                        while (yRevResult.next()) {
                            yRev = yRevResult.getFloat("yRevenue");
                        }
                    }

                    System.out.printf(
                            "| %-30s | $%-8.2f | $%-8.2f | $%-8.2f | $%-8.2f | $%-8.2f | $%-8.2f | $%-8.2f | $%-8.2f | $%-8.2f | $%-8.2f | $%-8.2f | $%-8.2f | $%-9.2f |%n",
                            roomName, mRev[0], mRev[1], mRev[2], mRev[3], mRev[4],
                            mRev[5], mRev[6], mRev[7], mRev[8], mRev[9], mRev[10],
                            mRev[11], yRev);
                }
            }

            System.out.printf(
                    "-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------%n");

            float[] mTotal = new float[12];
            float yTotal = 0;

            String mTotalSql = "SELECT MONTH(CheckOut) as month, SUM(ROUND(DATEDIFF(checkout, checkin) * rate, 2)) as mTotal FROM hp_reservations where YEAR(CheckOut) = YEAR(CURRENT_DATE()) group by month";
            String yTotalSql = "SELECT SUM(ROUND(DATEDIFF(checkout, checkin) * rate, 2)) as yTotal FROM hp_reservations where YEAR(CheckOut) = YEAR(CURRENT_DATE())";

            try (Statement mTotalStmt = conn.createStatement();
                    ResultSet mTotalResult = mTotalStmt.executeQuery(mTotalSql)) {
                while (mTotalResult.next()) {
                    int month = mTotalResult.getInt("month") - 1;
                    mTotal[month] = mTotalResult.getFloat("mTotal");
                }
            }

            try (Statement yTotalStmt = conn.createStatement();
                    ResultSet yTotalResult = yTotalStmt.executeQuery(yTotalSql)) {
                while (yTotalResult.next()) {
                    yTotal = yTotalResult.getFloat("yTotal");
                }
            }

            System.out.printf(
                    "| %-30s | $%-8.2f | $%-8.2f | $%-8.2f | $%-8.2f | $%-8.2f | $%-8.2f | $%-8.2f | $%-8.2f | $%-8.2f | $%-8.2f | $%-8.2f | $%-8.2f | $%-9.2f |%n",
                    "TOTALS", mTotal[0], mTotal[1], mTotal[2], mTotal[3], mTotal[4],
                    mTotal[5], mTotal[6], mTotal[7], mTotal[8], mTotal[9], mTotal[10],
                    mTotal[11], yTotal);
        }

        System.out.printf(
                "-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------%n");
    }
}