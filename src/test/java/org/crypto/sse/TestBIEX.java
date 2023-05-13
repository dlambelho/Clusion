package org.crypto.sse;

import database.DatabaseConnection;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;

import javax.crypto.NoSuchPaddingException;

public class TestBIEX {


    static int bigBlock = 100;
    static int smallBlock = 20;

    public static void main(String[] args) throws Exception {

        Scanner in = new Scanner(System.in);

        String option;


        DatabaseConnection.getInstance();
        DatabaseConnection.clearDatabase();

        List<byte[]> listSKs = new ArrayList<byte[]>();
        listSKs.add(new byte[32]);
        listSKs.add(new byte[32]);
        listSKs.add(new byte[32]);


        String pathName = "test/";

        ArrayList<File> listOfFile = new ArrayList<File>();


        TextProc.listf(pathName, listOfFile);

//				ArrayList<File> files = new ArrayList<File>();
//				files.add(new File(pathName));

        TextProc.TextProc(false, pathName);

        long start = System.currentTimeMillis();
        IEX2Lev disj = IEX2Lev.setup(listSKs, TextExtractPar.lp1, TextExtractPar.lp2, bigBlock, smallBlock, 0);
        long end = System.currentTimeMillis();
        System.out.println(end-start);


        // this is an example of how to perform boolean queries

        // number of disjunctions
        //int numDisjunctions = 2;
//        do {
//            System.out.print("> ");
//            option = in.nextLine().trim();
//            String[] conjunctions = option.split(" ");
//            if (!option.equals("exit")) {
//                // add three keys - here initialized to zeros for test purposes
//                // Storing the CNF form
//                String[][] query = new String[conjunctions.length][];
//                int c = 0;
//                for(String st : conjunctions) {
//                    query[c++] = st.split(",");
//                }
//
////                query[0] = "market".split(" ");  //TODO Make lowercase, this is done automatically by the parser
////                query[1] = "states".split(" ");
////                query[2] = "magia".split(" ");
//
//                Map<String, List<TokenDIS>> token = token_BIEX(listSKs, query);
//                query_BIEX(token);
//            }
//
//        } while (!option.equals("exit"));

    }


    public static Map<String, List<TokenDIS>> token_BIEX(List<byte[]> listSK, String[][] query) throws UnsupportedEncodingException {

        Map<String, List<TokenDIS>> token = new HashMap<String, List<TokenDIS>>();


        for (int i = 1; i < query.length; i++) {
            for (int k = 0; k < query[0].length; k++) {
                List<String> searchTMP = new ArrayList<String>();
                searchTMP.add(query[0][k]);

                for (int r = 0; r < query[i].length; r++) {
                    searchTMP.add(query[i][r]);
                }

                List<TokenDIS> tokenTMP = IEX2Lev.token(listSK, searchTMP);
                token.put(i+" "+k, tokenTMP);
            }
        }

        // Generate the IEX token
        List<String> searchBol = new ArrayList<String>();
        for (int i = 0; i < query[0].length; i++) {
            searchBol.add(query[0][i]);
        }
        List<TokenDIS> tokenGeneral = IEX2Lev.token(listSK, searchBol);
        token.put(query.length+" "+query[0].length, tokenGeneral);

        return token;
    }


    public static void query_BIEX(Map<String, List<TokenDIS>> token) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException,
            NoSuchProviderException, NoSuchPaddingException, UnsupportedEncodingException, IOException {


        int queryLength = 0;
        int firstQueryLength = 0;

        // determining the query length
        for (String label : token.keySet()) {

            String[] values = label.split(" ");


            if (Integer.parseInt(values[0]) > queryLength) {
                queryLength = Integer.parseInt(values[0]);
            }

            if (Integer.parseInt(values[1]) > firstQueryLength) {
                firstQueryLength = Integer.parseInt(values[1]);
            }

        }


        Set<String> tmpBol = IEX2Lev.query(token.get(queryLength+" "+firstQueryLength));


        for (int i = 1; i < queryLength; i++) {
            Set<String> finalResult = new HashSet<String>();
            for (int k = 0; k < firstQueryLength; k++) {

                List<TokenDIS> tokenTMP = token.get(i+" "+k);

                if (!(tmpBol.size() == 0)) {
                        int pos = 0;
                        for (int j = 0; j < tokenTMP.get(0).getTokenMMLocal().size(); j++) {

                            Set<String> temporary = new HashSet<String>();
                            List<String> tempoList = RR2Lev.altQuery(tokenTMP.get(0).getTokenMMLocal().get(j), "LOCAL_MAPS");

                            if (!(tempoList == null)) {
                                temporary = new HashSet<String>(
                                        RR2Lev.altQuery(tokenTMP.get(0).getTokenMMLocal().get(j), "LOCAL_MAPS"));
                            }

                            finalResult.addAll(temporary);

                            if (tmpBol.isEmpty()) {
                                break;
                            }

                        }

                }
            }
            tmpBol.retainAll(finalResult);

        }

        System.out.println("Final result " + tmpBol);


    }


}
