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
        try{
            InnReservations ir = new InnReservations();
            switch (Integer.parseInt(args[0])) {
                case 1: ir.roomsAndRates(); break;
                case 3: ir.reservationChange(); break;
                case 4: ir.reservationCancellation(); break;
            }
        }
        catch (SQLException e) {
            System.err.println("SQLException: " + e.getMessage());
        } catch (Exception e2) {
            System.err.println("Exception: " + e2.getMessage());
        }
    }

    //When this option is selected, the system shall output a list of rooms
    //to the user sorted by popularity
    //  •Room popularity score: number of days the room has been occupied during the previous
    //  180 days divided by 180 (round to two decimal places)
    //  •Next available check-in date.
    //  •Length in days and check out date of the most recent (completed) stay in the room.
    private void roomsAndRates() throws SQLException{

        System.out.println("FR1: Rooms and Rates\r\n");

        try (Connection conn = DriverManager.getConnection(System.getenv("HP_JDBC_URL"),
                System.getenv("HP_JDBC_USER"),
                System.getenv("HP_JDBC_PW"))) {

            String roomQuery = "SELECT * FROM hp_rooms";
            String reservationQuery = "SELECT * FROM hp_reservations where CODE = 69420";

            try (Statement stmt = conn.createStatement();
                ResultSet reservationSet = stmt.executeQuery(reservationQuery)) {
                    System.out.println(reservationSet.first());
//                    reservationSet.next();
//                    System.out.println(reservationSet.getString(1));
                }
        }
    }

    private void reservationChange() throws SQLException{
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

            //Checks if the checkout date comes before the check-in date
            if (checkOut.compareTo(checkIn) == -1) {
                System.out.println("Check out date cannot be before check in");
                return;
            }

            //If the check-in or check-out dates have been updated, this is to check if they conflict
            //with any other reservations in the same room
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

                StringBuilder dateChecker = new StringBuilder("SELECT * FROM hp_reservations WHERE room = ? AND CheckIn < ? AND Checkout > ? ");
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
                            System.out.println("The new check-in or check-out date conflicts with another reservation in the same room");
                            return;
                        }
                    }
                }
            }

            //The program reaches here if there was no time conflict or updated check-in or check-out dates
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
                if (first == 0){
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
}
