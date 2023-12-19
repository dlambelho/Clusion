package booleanCash;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import database.DatabaseConnection;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.crypto.NoSuchPaddingException;
import org.crypto.sse.CryptoPrimitives;
import org.crypto.sse.TextExtractPar;
import org.crypto.sse.TextProc;

public class BXTSearch {

    private static final String INSERT_BXTTSET = "INSERT INTO BXT_TSET (WORD , FILE_ENC) VALUES (?, ?)";
    private static final String INSERT_BXTXSET = "INSERT INTO BXT_XSET (CROSS_TAG) VALUES (?)";

    private static final String SELECT_BXTTSET = "SELECT FILE_ENC FROM BXT_TSET WHERE WORD = ?";
    private static final String SELECT_BXTXSET = "SELECT COUNT(*) FROM BXT_XSET WHERE CROSS_TAG = ?";

    private static final String UPDATE_BXTTSET = "UPDATE CLUSION.BXT_TSET SET FILE WHERE WORD LIKE ?";

    static Multimap<byte[], byte[]> Tset = ArrayListMultimap.create();
    HashSet<String> xSet;

    public static SecureRandom random = new SecureRandom();

    public static int sizeOfFileIdentifer = 150;

    static byte[] keyS = new byte[32];
    static byte[] keyX = new byte[32];


    public static void setup(Multimap<String, String> lookup)
            throws IOException, InvalidAlgorithmParameterException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, NoSuchProviderException {

        for (String word : lookup.keySet()) {
            byte[] keyE = CryptoPrimitives.generateHmac(keyS, word);
            byte[] xtrap = CryptoPrimitives.generateHmac(keyX, word);


            byte[] iv1 = new byte[16];

            byte[] encWord = CryptoPrimitives.encryptAES_CTR_String(keyE, iv1, word, sizeOfFileIdentifer);

            try (PreparedStatement statement = DatabaseConnection.getInstance().prepareStatement(INSERT_BXTTSET);
                 PreparedStatement statement2 = DatabaseConnection.getInstance().prepareStatement(INSERT_BXTXSET)) {

                for (String file : lookup.get(word)) {
                    byte[] iv = new byte[16];
                    random.nextBytes(iv);

                    byte[] encF = CryptoPrimitives.encryptAES_CTR_String(keyE, iv, file, sizeOfFileIdentifer);
                    byte[] xtag = CryptoPrimitives.generateHmac(xtrap, file);

                    Tset.put(encWord, encF);

                    statement.setString(1, new String(Base64.getEncoder().encode(encWord)));
                    statement.setBytes(2, Base64.getEncoder().encode(encF));
                    statement.addBatch();

                    statement2.setString(1, new String(Base64.getEncoder().encode(xtag)));
                    statement2.addBatch();

                }

                statement.executeBatch();
                statement2.executeBatch();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void updateScheme(Multimap<String, String> lookup)
            throws InvalidAlgorithmParameterException, NoSuchPaddingException, NoSuchAlgorithmException, IOException,
            InvalidKeyException, NoSuchProviderException {

        for (String word : lookup.keySet()) {
            byte[] keyE = CryptoPrimitives.generateHmac(keyS, word);
            byte[] xtrap = CryptoPrimitives.generateHmac(keyX, word);


            byte[] iv1 = new byte[16];

            byte[] encWord = CryptoPrimitives.encryptAES_CTR_String(keyE, iv1, word, sizeOfFileIdentifer);

            for (String file : lookup.get(word)) {
                byte[] iv = new byte[16];
                random.nextBytes(iv);

                byte[] encF = CryptoPrimitives.encryptAES_CTR_String(keyE, iv, file, sizeOfFileIdentifer);
                byte[] xtag = CryptoPrimitives.generateHmac(xtrap, file);

                Tset.put(encWord, encF);

                try (PreparedStatement statement = DatabaseConnection.getInstance().prepareStatement(INSERT_BXTTSET)) {
                    statement.setString(1, new String(Base64.getEncoder().encode(encWord)));
                    statement.setBytes(2, Base64.getEncoder().encode(encF));
                    statement.execute();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }

                try (PreparedStatement statement = DatabaseConnection.getInstance().prepareStatement(INSERT_BXTXSET)) {
                    statement.setString(1, new String(Base64.getEncoder().encode(xtag)));
                    statement.execute();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }


    public static void search(List<String> keywords)
            throws IOException, InvalidAlgorithmParameterException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, NoSuchProviderException {
        String firstWord = keywords.remove(0);

        byte[] keyE = CryptoPrimitives.generateHmac(keyS, firstWord);

        byte[] iv1 = new byte[16];

        byte[] encWord = CryptoPrimitives.encryptAES_CTR_String(keyE, iv1, firstWord, sizeOfFileIdentifer);

        List<byte[]> encFiles = new ArrayList<>();

        ResultSet result;
        try (PreparedStatement statement = DatabaseConnection.getInstance().prepareStatement(SELECT_BXTTSET)) {
            statement.setString(1, new String(Base64.getEncoder().encode(encWord)));
            result = statement.executeQuery();

            while (result.next()) {
                encFiles.add(Base64.getDecoder().decode(result.getBytes(1)));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        List<String> files = new ArrayList<>();
        for (byte[] encFile : encFiles) {
            files.add(new String(CryptoPrimitives.decryptAES_CTR_String(encFile, keyE), StandardCharsets.UTF_8).trim());
        }
        Set<String> resultSet = new HashSet<>(files);
        for (String word : keywords) {
            byte[] xtrap = CryptoPrimitives.generateHmac(keyX, word);

            for (String file : files) {
                byte[] xtag = CryptoPrimitives.generateHmac(xtrap, file);

                try (PreparedStatement statement = DatabaseConnection.getInstance().prepareStatement(SELECT_BXTXSET)) {
                    statement.setString(1, new String(Base64.getEncoder().encode(xtag)));
                    result = statement.executeQuery();

                    if (result.next()) {
                        if (result.getInt(1) <= 0) {
                            resultSet.remove(file);
                        }
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        System.out.println(resultSet.stream().sorted().toList());
    }

    public static void main(String[] args)
            throws InvalidAlgorithmParameterException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeySpecException, InvalidKeyException, NoSuchProviderException, ExecutionException,
            InterruptedException, IOException {
        String pathName = args[1];


        ArrayList<File> listOfFile = new ArrayList<File>();

        TextProc.listf(pathName, listOfFile);

        TextProc.TextProc(false, pathName);

        for (int i = 0; i < Integer.parseInt(args[0]); i++) {
            DatabaseConnection.getInstance();
            DatabaseConnection.clearDatabase();
            long start = System.currentTimeMillis();

            setup(TextExtractPar.lp1);

            long end = System.currentTimeMillis();
            System.out.println(end-start);

        }

//        ArrayList<String> keywords;
//
//        Scanner in = new Scanner(System.in);
//        String option = "";
//
//        do {
//            try {
//                System.out.print("> ");
//                option = in.nextLine().trim().toLowerCase();
//
//                if (option.contains("add_file")) {
//                    File file = new File(option.substring(8).trim());
//                    BXTSearch.updateScheme(TextExtractPar.extractOneDoc(new File[] {file}).getL1());
//                } else {
//                    String[] conjunctions = option.split(" ");
//
//                    if (!option.equals("exit")) {
//                        keywords = new ArrayList<>(List.of(conjunctions));
//
//                        search(keywords);
//                    }
//                }
//            } catch (IOException exception) {
//                System.out.println(exception.getMessage());
//            }
//
//        } while (!option.equals("exit"));

    }
}
