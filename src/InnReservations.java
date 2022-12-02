import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class InnReservations {
    public static void main(String[] args) {
        System.out.println(System.getenv());
        try {
            InnReservations ir = new InnReservations();
            switch (Integer.parseInt(args[0])) {
                case 1:
                    ir.roomsAndRates();
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

            String roomsAndRates = "with Popular as (SELECT Room, ((SUM(DateDiff(Checkout, IF((CheckIn >= DATE_SUB(CURRENT_DATE, INTERVAL 180 day)), CheckIn, DATE_SUB(CURRENT_DATE, INTERVAL 180 day)))))/180) as Popularity FROM hp_reservations WHERE CheckOut >= (DATE_SUB(CURRENT_DATE, INTERVAL 180 day)) GROUP BY Room), mostRecent as (SELECT hp_rooms.RoomName as RoomName, hp_reservations.Room as Room, MAX(Checkout) as recentCheckout, MAX(CheckIn) as recentCheckIn FROM hp_reservations INNER JOIN hp_rooms ON hp_reservations.Room = hp_rooms.RoomCode GROUP By hp_reservations.Room, hp_rooms.RoomName) SELECT RoomName, mostRecent.Room, Popularity, DATE_ADD(recentCheckout, INTERVAL 1 day), DATEDIFF(recentCheckout, recentCheckIn) FROM mostRecent INNER JOIN Popular ON Popular.Room = mostRecent.Room";

            try (Statement stmt = conn.createStatement()) {
                ResultSet result = stmt.executeQuery(roomsAndRates);
                System.out.println("RoomName" + "\t" + "Popularity" + "\t" + "Next Avalible CheckIn" + "\t"
                        + "Recent Stay Length");
                while (result.next()) {
                    String roomName = result.getString(1);
                    String popularity = result.getString(2);
                    String nextCheckIn = result.getString(3);
                    String recentStayLength = result.getString(4);
                    System.out.println(roomName + "\t" + popularity + "\t" + nextCheckIn + "\t" + recentStayLength);
                }
            }
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

            String First;
            String Last;
            String CheckInDate;
            String CheckoutDate;
            String RoomCode;
            String ReservationCode;

            Scanner scanner = new Scanner(System.in);
            System.out.println("Enter any combination of the fields listed below. A blank entry should indicate Any");
            // First name
            System.out.println("Enter a first name: ");
            if (scanner.nextLine().equals("")) {
                First = "%%";
            } else {
                First = scanner.nextLine();
            }
            // Last name
            System.out.println("Enter a last name: ");
            if (scanner.nextLine().equals("")) {
                Last = "%%";
            } else {
                Last = scanner.nextLine();
            }
            // A range of dates
            System.out.println("Enter a range of dates(xxxx-x-x : xxxx-x-x) : ");
            if (scanner.nextLine().equals("")) {
                CheckInDate = "> 0000-1-1";
                CheckoutDate = "< 9999-12-31";
            } else {
                String[] dates = (scanner.nextLine()).split(":");
                System.out.print(dates);
                CheckInDate = "=" + dates[0];
                CheckoutDate = "=" + dates[1];
            }
            // Room code
            System.out.println("Enter a room code: ");
            if (scanner.nextLine().equals("")) {
                RoomCode = "%%";
            } else {
                RoomCode = scanner.nextLine();
            }
            // Reservation code
            System.out.println("Enter a reservation code: ");
            if (scanner.nextLine().equals("")) {
                ReservationCode = "%%";
            } else {
                ReservationCode = scanner.nextLine();
            }

            String reservationInfo = "SELECT CODE, Room, RoomName, Checkin, Checkout, Rate, LastName, FirstName, Adults, Kids FROM hp_reservations INNER JOIN hp_rooms on hp_reservations.Room = hp_rooms.RoomCode WHERE Room LIKE"
                    + RoomCode + "AND CODE LIKE" + ReservationCode + "AND FirstName LIKE" + First + "AND LastName LIKE"
                    + Last + "AND Checkin" + CheckInDate + "AND Checkout" + CheckoutDate;

            try (Statement stmt = conn.createStatement()) {
                ResultSet result = stmt.executeQuery(reservationInfo);
                System.out.println("CODE" + "\t" + "Room" + "\t" + "RoomName" + "\t"
                        + "Checkin" + "\t" + "Checkout" + "\t" + "Rate" + "\t" + "LastName" + "\t" + "FirstName" + "\t"
                        + "Adults" + "\t" + "Kids");
                while (result.next()) {
                    String CODE = result.getString(0);
                    String Room = result.getString(1);
                    String RoomName = result.getString(2);
                    String CheckIn = result.getString(3);
                    String Checkout = result.getString(4);
                    String Rate = result.getString(5);
                    String LastName = result.getString(6);
                    String FirstName = result.getString(7);
                    String Adults = result.getString(8);
                    String Kids = result.getString(9);
                    System.out.println(CODE + "\t" + Room + "\t" + RoomName + "\t" + CheckIn + "\t" + Checkout + "\t"
                            + Rate + "\t" + LastName + "\t" + FirstName + "\t" + Adults + "\t" + Kids);
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