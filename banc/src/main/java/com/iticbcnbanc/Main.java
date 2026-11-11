package com.iticbcnbanc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool.PoolInitializationException;
 
 
public class Main {
    public static void main(String[] args) throws IOException{
        Properties prop = new Properties();
        prop.load(Files.newBufferedReader(Path.of("banc","src","main","resources","conn.properties")));
        HikariConfig hkcfg = new HikariConfig();
        //parametria de la connexió
        hkcfg.setJdbcUrl(prop.getProperty("DB_URL"));
        hkcfg.setUsername(prop.getProperty("DB_USER"));
        hkcfg.setPassword(prop.getProperty("DB_PASSWORD"));
        hkcfg.setMaximumPoolSize(Integer.parseInt(prop.getProperty("DB_MAX_POOL", "5")));
        
        //iniciem el datasource i controlem excepcions
       try (HikariDataSource hkds = new HikariDataSource(hkcfg)) {
            //connectem la base de dades
            try (Connection cnn = hkds.getConnection()){
                for (String resultat: readAllStatements(cnn)) {
                    System.out.println("moviment amb id: " + resultat);
                }
               

                // Statements amb import >= quantitat
               readStatementsByAmount(cnn, 1000.0);

                // Alta de client
                Scanner sc = new Scanner(System.in);
                List<String> dadesClient = new ArrayList<>();

                System.out.print("Nom: ");
                dadesClient.add(sc.nextLine().trim());
                System.out.print("Cognoms: ");
                dadesClient.add(sc.nextLine().trim());
                System.out.print("ID fiscal: ");
                dadesClient.add(sc.nextLine().trim());

                insertClient(cnn, dadesClient);
               sc.close();
            } catch (SQLException e) {
                System.out.println("No connectat!");
                e.printStackTrace();
            }


        } catch (PoolInitializationException e) {  
            System.out.println("No s'ha pogut connectar a la base de dades: " + e.getMessage());
        }

    }
    public static List<String> readAllStatements (Connection conn) throws SQLException {
        List<String> resultatQuery = new ArrayList<String>();
        String query = "SELECT * FROM statements";
        PreparedStatement ps = conn.prepareStatement(query);
       
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String resultat = String.join(";",rs.getString("id_statement"),
                                                            rs.getString("tipus_moviment"),
                                                            rs.getString("import"),
                                                            rs.getString("divisa"));
                resultatQuery.add(resultat);
            }
        }


        return resultatQuery;
    }

    
    public static void readStatementsByAmount(Connection conn, double quantitat) throws SQLException {
    String query = "SELECT * FROM statements WHERE import >= ?";

        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setDouble(1, quantitat);

            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                String header = String.join(";",
                        meta.getColumnName(1),
                        meta.getColumnName(3),
                        meta.getColumnName(8),
                        meta.getColumnName(10));
                System.out.println(header);

                while (rs.next()) {
                    String resultat = String.join(";",
                            rs.getString("id_statement"),
                            rs.getString("tipus_moviment"),
                            rs.getString("import"),
                            rs.getString("divisa"));
                    System.out.println("moviment amb id: " + resultat);
                }
            }
        }
    }

    public static boolean clientExisteix(Connection conn, String idFiscal) throws SQLException {
        String query = "SELECT 1 FROM clients WHERE id_fiscal = ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, idFiscal);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static void insertClient(Connection conn, List<String> dades) throws SQLException {
        String nom = dades.get(0);
        String cognoms = dades.get(1);
        String idFiscal = dades.get(2);

        boolean autoCommitOriginal = conn.getAutoCommit();
        conn.setAutoCommit(false);

        try {
            if (clientExisteix(conn, idFiscal)) {
                System.out.println("El client amb id fiscal " + idFiscal + " ja existeix. No s'insereix.");
                conn.rollback();
                return;
            }

            String insert = "INSERT INTO clients (nom,cognoms,id_fiscal) VALUES (?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                ps.setString(1, nom);
                ps.setString(2, cognoms);
                ps.setString(3, idFiscal);
                ps.executeUpdate();
            }

            conn.commit();
            System.out.println("Client inserit correctament.");

        } catch (SQLException e) {
            conn.rollback();
            System.out.println("Error en la inserció, s'ha fet rollback: " + e.getMessage());
            throw e;
        } finally {
            conn.setAutoCommit(autoCommitOriginal); 
        }
    }
}
