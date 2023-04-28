/**
 * Copyright (C) 2016 Tarik Moataz
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.crypto.sse;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import database.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedList;
import javax.crypto.NoSuchPaddingException;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.*;
import javax.sql.rowset.serial.SerialClob;
import javax.xml.crypto.Data;
import org.apache.commons.collections.list.SetUniqueList;

//***********************************************************************************************//

/////////////////////    Implementation of 2Lev scheme of NDSS'14 paper by David Cash Joseph Jaeger Stanislaw Jarecki  Charanjit Jutla Hugo Krawczyk Marcel-Catalin Rosu and Michael Steiner. Finding 
//		the right parameters--- of the array size as well as the threshold to differentiate between large and small database,  to meet the same reported benchmarks is empirically set in the code
//		as it was not reported in the paper. The experimental evaluation of the  scheme is one order of magnitude slower than the numbers reported by Cash as we use Java and not C
//		Plus, some enhancements on the code itself that can be done.

///		This class can be used independently of the IEX-2Lev or IEX-ZMF if needed /////////////////////////////

//***********************************************************************************************//	

public class RR2Lev implements Serializable {

	// define the number of character that a file identifier can have
	public static int sizeOfFileIdentifer = 40;

	// instantiate the Secure Random Object
	public static SecureRandom random = new SecureRandom();

	public static int counter = 0;

	public Multimap<String, byte[]> dictionary = ArrayListMultimap.create();
	public static List<Integer> free = new ArrayList<Integer>();
	static byte[][] array = null;
	byte[][] arr = null;

	public RR2Lev(Multimap<String, byte[]> dictionary, byte[][] arr) {
		this.dictionary = dictionary;
		this.arr = arr;
	}

	public Multimap<String, byte[]> getDictionary() {
		return dictionary;
	}

	public void setDictionary(Multimap<String, byte[]> dictionary) {
		this.dictionary = dictionary;
	}

	public byte[][] getArray() {
		return arr;
	}

	public void setArray(byte[][] array) {
		this.arr = array;
	}

	// ***********************************************************************************************//

	///////////////////// Key Generation /////////////////////////////

	// ***********************************************************************************************//

	public static byte[] keyGen(int keySize, String password, String filePathString, int icount)
			throws InvalidKeySpecException, NoSuchAlgorithmException, NoSuchProviderException {
		File f = new File(filePathString);
		byte[] salt = null;

		if (f.exists() && !f.isDirectory()) {
			salt = CryptoPrimitives.readAlternateImpl(filePathString);
		} else {
			salt = CryptoPrimitives.randomBytes(8);
			CryptoPrimitives.write(salt, "saltInvIX", "salt");

		}

		byte[] key = CryptoPrimitives.keyGenSetM(password, salt, icount, keySize);
		return key;

	}

	// ***********************************************************************************************//

	///////////////////// Setup Parallel/////////////////////////////

	// ***********************************************************************************************//

	public static RR2Lev constructEMMParGMM(final byte[] key, final Multimap<String, String> lookup, final int bigBlock,
			final int smallBlock, final int dataSize, String table) throws InterruptedException, ExecutionException, IOException {

		final Multimap<String, byte[]> dictionary = ArrayListMultimap.create();

		random.setSeed(CryptoPrimitives.randomSeed(16));

		for (int i = 0; i < dataSize; i++) {
			// initialize all buckets with random values
			free.add(i);
		}

		List<String> listOfKeyword = new ArrayList<String>(lookup.keySet());
		int threads = 0;
		if (Runtime.getRuntime().availableProcessors() > listOfKeyword.size() && listOfKeyword.size() != 0) {
			threads = listOfKeyword.size();
		} else {
			threads = Runtime.getRuntime().availableProcessors();
		}

		ExecutorService service = Executors.newFixedThreadPool(threads);
		ArrayList<String[]> inputs = new ArrayList<String[]>(threads);

		for (int i = 0; i < threads; i++) {
			String[] tmp;
			if (i == threads - 1) {
				tmp = new String[listOfKeyword.size() / threads + listOfKeyword.size() % threads];
				for (int j = 0; j < listOfKeyword.size() / threads + listOfKeyword.size() % threads; j++) {
					tmp[j] = listOfKeyword.get((listOfKeyword.size() / threads) * i + j);
				}
			} else {
				tmp = new String[listOfKeyword.size() / threads];
				for (int j = 0; j < listOfKeyword.size() / threads; j++) {

					tmp[j] = listOfKeyword.get((listOfKeyword.size() / threads) * i + j);
				}
			}
			inputs.add(i, tmp);
		}

		Printer.debugln("End of Partitionning  \n");

		List<Future<Multimap<String, byte[]>>> futures = new ArrayList<Future<Multimap<String, byte[]>>>();
		for (final String[] input : inputs) {
			Callable<Multimap<String, byte[]>> callable = new Callable<Multimap<String, byte[]>>() {
				public Multimap<String, byte[]> call() throws Exception {

					Multimap<String, byte[]> output = altSetup(key, input, lookup, bigBlock, smallBlock);
					return output;
				}
			};
			futures.add(service.submit(callable));
		}

		service.shutdown();

		for (Future<Multimap<String, byte[]>> future : futures) {

			Collection<Map.Entry<String, byte[]>> keys = future.get().entries();

			for (Map.Entry<String, byte[]> k : keys) {
				String checkEntry = "SELECT COUNT(*) FROM CLUSION." + table + " WHERE HMAC LIKE ?";

				String stat;
				try (PreparedStatement statement = DatabaseConnection.getInstance().prepareStatement(checkEntry)) {
					statement.setString(1, k.getKey());
					ResultSet result = statement.executeQuery();
					result.next();
					if(result.getInt(1) <= 0) {
						stat = "INSERT INTO CLUSION." + table + " (IDENTIFIER, HMAC) VALUES (?, ?)";
					} else {
						stat = "UPDATE CLUSION." + table + " SET IDENTIFIER = ? WHERE HMAC = ?";
					}
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}


				try (PreparedStatement statement = DatabaseConnection.getInstance().prepareStatement(stat)) {

					statement.setString(2, k.getKey());
					statement.setBytes(1, Base64.getEncoder().encode(k.getValue()));

					statement.executeUpdate();
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
			}

		}

		return new RR2Lev(dictionary, array);
	}

	// ***********************************************************************************************//

	///////////////////// Setup /////////////////////////////

	// ***********************************************************************************************//

	public static Multimap<String, byte[]> setup(byte[] key, String[] listOfKeyword, Multimap<String, String> lookup,
			int bigBlock, int smallBlock, int dataSize) throws InvalidKeyException, InvalidAlgorithmParameterException,
			NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException, IOException {

		// determine the size f the data set and therefore the size of the array
		array = new byte[dataSize][];
		Multimap<String, byte[]> gamma = ArrayListMultimap.create();
		long startTime = System.nanoTime();

        byte[] iv = new byte[16];

		for (String word : listOfKeyword) {

			counter++;
			if (((float) counter / 10000) == (int) (counter / 10000)) {
				Printer.debugln("Number of processed keywords " + counter);
			}

			// generate the tag
			byte[] key1 = CryptoPrimitives.generateHmac(key, 1 + word);
			byte[] key2 = CryptoPrimitives.generateHmac(key, 2 + word);
			int t = (int) Math.ceil((float) lookup.get(word).size() / bigBlock);

			if (lookup.get(word).size() <= smallBlock) {
				// pad DB(w) to "small block"
				byte[] l = CryptoPrimitives.generateHmac(key1, Integer.toString(0));
				random.nextBytes(iv);
				byte[] v =CryptoPrimitives.encryptAES_CTR_String(key2, iv,
						"1 " + lookup.get(word).toString(), smallBlock * sizeOfFileIdentifer);
				gamma.put(new String(l), v);
			}

			else {

				List<String> listArrayIndex = new ArrayList<String>();

				for (int j = 0; j < t; j++) {

					List<String> tmpList = new ArrayList<String>(lookup.get(word));

					if (j != t - 1) {
						tmpList = tmpList.subList(j * bigBlock, (j + 1) * bigBlock);
					} else {
						int sizeList = tmpList.size();

						tmpList = tmpList.subList(j * bigBlock, tmpList.size());

						for (int s = 0; s < ((j + 1) * bigBlock - sizeList); s++) {
							tmpList.add("XX");
						}

					}

					// generate the integer which is associated to free[b]

					byte[] randomBytes = CryptoPrimitives
							.randomBytes((int) Math.ceil(((float) Math.log(free.size()) / (Math.log(2) * 8))));

					int position = CryptoPrimitives.getIntFromByte(randomBytes,
							(int) Math.ceil(Math.log(free.size()) / Math.log(2)));

					while (position >= free.size() - 1) {
						position = position / 2;
					}

					int tmpPos = free.get(position);
					random.nextBytes(iv);

					array[tmpPos] = CryptoPrimitives.encryptAES_CTR_String(key2, iv,
							tmpList.toString(), bigBlock * sizeOfFileIdentifer);
					listArrayIndex.add(tmpPos + "");

					free.remove(position);

				}

				// medium case
				if (t <= smallBlock) {
					byte[] l = CryptoPrimitives.generateHmac(key1, Integer.toString(0));
					random.nextBytes(iv);
					byte[] v = CryptoPrimitives.encryptAES_CTR_String(key2, iv,
									"2 " + listArrayIndex.toString(), smallBlock * sizeOfFileIdentifer);
					gamma.put(new String(l),v);
				}
				// big case
				else {
					int tPrime = (int) Math.ceil((float) t / bigBlock);

					List<String> listArrayIndexTwo = new ArrayList<String>();

					for (int l = 0; l < tPrime; l++) {
						List<String> tmpListTwo = new ArrayList<String>(listArrayIndex);

						if (l != tPrime - 1) {
							tmpListTwo = tmpListTwo.subList(l * bigBlock, (l + 1) * bigBlock);
						} else {

							int sizeList = tmpListTwo.size();

							tmpListTwo = tmpListTwo.subList(l * bigBlock, tmpListTwo.size());
							for (int s = 0; s < ((l + 1) * bigBlock - sizeList); s++) {
								tmpListTwo.add("XX");
							}
						}

						// generate the integer which is associated to free[b]

						byte[] randomBytes = CryptoPrimitives
								.randomBytes((int) Math.ceil((Math.log(free.size()) / (Math.log(2) * 8))));

						int position = CryptoPrimitives.getIntFromByte(randomBytes,
								(int) Math.ceil(Math.log(free.size()) / Math.log(2)));

						while (position >= free.size()) {
							position = position / 2;
						}

						int tmpPos = free.get(position);
						random.nextBytes(iv);

						array[tmpPos] = CryptoPrimitives.encryptAES_CTR_String(key2, iv,
								tmpListTwo.toString(), bigBlock * sizeOfFileIdentifer);
						listArrayIndexTwo.add(tmpPos + "");
						free.remove(position);

					}

					// Pad the second set of identifiers

					byte[] l = CryptoPrimitives.generateHmac(key1, Integer.toString(0));
					random.nextBytes(iv);
					byte[] v = CryptoPrimitives.encryptAES_CTR_String(key2, iv,
							"3 " + listArrayIndexTwo.toString(), smallBlock * sizeOfFileIdentifer);
					gamma.put(new String(l),v);
				}

			}

		}
		long endTime = System.nanoTime();
		long totalTime = endTime - startTime;
		// Printer.debugln("Time for one (w, id) "+totalTime/lookup.size());
		return gamma;
	}

	// ***********************************************************************************************//

	///////////////////// Search Token generation /////////////////////
	///////////////////// /////////////////////////////

	// ***********************************************************************************************//

	public static byte[][] token(byte[] key, String word) throws UnsupportedEncodingException {

		byte[][] keys = new byte[2][];
		keys[0] = CryptoPrimitives.generateHmac(key, 1 + word);
		keys[1] = CryptoPrimitives.generateHmac(key, 2 + word);

		return keys;
	}

	// ***********************************************************************************************//

	///////////////////// Query Alg /////////////////////////////

	// ***********************************************************************************************//

	public static List<String> query(byte[][] keys, String table)
			throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException,
			NoSuchProviderException, NoSuchPaddingException, IOException {

		byte[] l = CryptoPrimitives.generateHmac(keys[0], Integer.toString(0));

		List<byte[]> tempList = new ArrayList<>();

		String fetch = "SELECT IDENTIFIER FROM CLUSION." + table + " WHERE HMAC LIKE ?";
		try (PreparedStatement statement = DatabaseConnection.getInstance().prepareStatement(fetch)) {
			statement.setString(1, new String(l));
			ResultSet result = statement.executeQuery();
			if (result.next())
				tempList = List.of(Base64.getDecoder().decode(result.getBytes(1)));

		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		if (!(tempList.size() == 0)) {
			String temp = (new String(CryptoPrimitives.decryptAES_CTR_String(tempList.get(0), keys[1])))
					.split("\t\t\t")[0];
			temp = temp.replaceAll("\\s", "");
			temp = temp.replace('[', ',');
			temp = temp.replace("]", "");

			String[] result = temp.split(",");

			List<String> resultFinal = new ArrayList<String>(Arrays.asList(result));
			// We remove the flag that identifies the size of the dataset

			if (result[0].equals("1")) {

				resultFinal.remove(0);
				return resultFinal;
			}

			else if (result[0].equals("2")) {
				resultFinal.remove(0);

				List<String> resultFinal2 = new ArrayList<String>();

				for (String key : resultFinal) {

					boolean flag = true;
					int counter = 0;
					while (flag) {

						if (counter < key.length() && Character.isDigit(key.charAt(counter))) {

							counter++;
						}

						else {
							flag = false;
						}
					}

					String temp2 = "";
					if (!(array[Integer.parseInt((String) key.subSequence(0, counter))] == null)) {
						temp2 = (new String(CryptoPrimitives.decryptAES_CTR_String(
								array[Integer.parseInt((String) key.subSequence(0, counter))], keys[1])))
										.split("\t\t\t")[0];
					}
					temp2 = temp2.replaceAll("\\s", "");

					temp2 = temp2.replaceAll(",XX", "");

					temp2 = temp2.replace("[", "");
					temp2 = temp2.replace("]", "");

					String[] result3 = temp2.split(",");

					List<String> tmp = new ArrayList<String>(Arrays.asList(result3));

					resultFinal2.addAll(tmp);
				}

				return resultFinal2;
			}

			else if (result[0].equals("3")) {
				resultFinal.remove(0);
				List<String> resultFinal2 = new ArrayList<String>();
				for (String key : resultFinal) {

					boolean flag = true;
					int counter = 0;
					while (flag) {

						if (counter < key.length() && Character.isDigit(key.charAt(counter))) {

							counter++;
						}

						else {
							flag = false;
						}
					}
					String temp2 = (new String(CryptoPrimitives.decryptAES_CTR_String(
							array[Integer.parseInt((String) key.subSequence(0, counter))], keys[1])))
									.split("\t\t\t")[0];
					temp2 = temp2.replaceAll("\\s", "");

					temp2 = temp2.replaceAll(",XX", "");
					temp2 = temp2.replace("[", "");
					temp2 = temp2.replace("]", "");

					String[] result3 = temp2.split(",");
					List<String> tmp = new ArrayList<String>(Arrays.asList(result3));
					resultFinal2.addAll(tmp);
				}
				List<String> resultFinal3 = new ArrayList<String>();

				for (String key : resultFinal2) {

					boolean flag = true;
					int counter = 0;
					while (flag) {

						if (counter < key.length() && Character.isDigit(key.charAt(counter))) {

							counter++;
						}

						else {
							flag = false;
						}
					}
					String temp2 = (new String(CryptoPrimitives.decryptAES_CTR_String(
							array[Integer.parseInt((String) key.subSequence(0, counter))], keys[1])))
									.split("\t\t\t")[0];
					temp2 = temp2.replaceAll("\\s", "");
					temp2 = temp2.replaceAll(",XX", "");

					temp2 = temp2.replace("[", "");
					temp2 = temp2.replace("]", "");
					String[] result3 = temp2.split(",");

					List<String> tmp = new ArrayList<String>(Arrays.asList(result3));

					resultFinal3.addAll(tmp);
				}

				return resultFinal3;
			}
		}
		return new ArrayList<String>();
	}
    public static Multimap<String, byte[]> altSetup(byte[] key, String[] listOfKeyword, Multimap<String, String> lookup, int smallBlock, int bigBlock)
            throws IOException, InvalidAlgorithmParameterException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, NoSuchProviderException {

        Multimap<String, byte[]> gamma = ArrayListMultimap.create();
        long startTime = System.nanoTime();

        byte[] iv = new byte[16];

        for (String word : listOfKeyword) {

            counter++;
            if (((float) counter / 10000) == (int) (counter / 10000)) {
                Printer.debugln("Number of processed keywords " + counter);
            }

            // generate the tag
            byte[] key1 = CryptoPrimitives.generateHmac(key, 1 + word);
            byte[] key2 = CryptoPrimitives.generateHmac(key, 2 + word);
            int t = (int) Math.ceil((float) lookup.get(word).size() / bigBlock);

            if (lookup.get(word).size() <= smallBlock) {
                // pad DB(w) to "small block"
                byte[] l = CryptoPrimitives.generateHmac(key1, Integer.toString(0));
                random.nextBytes(iv);
                byte[] v = CryptoPrimitives.encryptAES_CTR_String(key2, iv,
                        "1 " + lookup.get(word).toString(), smallBlock * sizeOfFileIdentifer);
                gamma.put(new String(l), v);
            } else {

                List<String> listArrayIndex = new ArrayList<String>();

                for (int j = 0; j < t; j++) {

                    List<String> tmpList = new ArrayList<String>(lookup.get(word));

                    if (j != t - 1) {
                        tmpList = tmpList.subList(j * bigBlock, (j + 1) * bigBlock);
                    } else {
                        int sizeList = tmpList.size();

                        tmpList = tmpList.subList(j * bigBlock, tmpList.size());

                        for (int s = 0; s < ((j + 1) * bigBlock - sizeList); s++) {
                            tmpList.add("XX");
                        }

                    }

                    // generate the integer which is associated to free[b]

                    byte[] randomBytes = CryptoPrimitives
                            .randomBytes((int) Math.ceil(((float) Math.log(free.size()) / (Math.log(2) * 8))));

                    int position = CryptoPrimitives.getIntFromByte(randomBytes,
                            (int) Math.ceil(Math.log(free.size()) / Math.log(2)));

                    while (position >= free.size() - 1) {
                        position = position / 2;
                    }

                    int tmpPos = free.get(position);
                    random.nextBytes(iv);

                    byte[] v = CryptoPrimitives.encryptAES_CTR_String(key2, iv,
                            "1 " + tmpList.toString(), bigBlock * sizeOfFileIdentifer);
					byte[] l = CryptoPrimitives.randomBytes(32);
					String id = new String(Base64.getEncoder().encode(l));
					listArrayIndex.add(id);
					gamma.put(id,v);

                }

                // medium case
                if (t <= smallBlock) {
                    byte[] l = CryptoPrimitives.generateHmac(key1, Integer.toString(0));
                    random.nextBytes(iv);
                    byte[] v = CryptoPrimitives.encryptAES_CTR_String(key2, iv,
                            "2 " + listArrayIndex.toString(), smallBlock * sizeOfFileIdentifer);
                    gamma.put(new String(l), v);
                }
                // big case
                else {
                    int tPrime = (int) Math.ceil((float) t / bigBlock);

                    List<String> listArrayIndexTwo = new ArrayList<String>();

                    for (int l = 0; l < tPrime; l++) {
                        List<String> tmpListTwo = new ArrayList<String>(listArrayIndex);

                        if (l != tPrime - 1) {
                            tmpListTwo = tmpListTwo.subList(l * bigBlock, (l + 1) * bigBlock);
                        } else {

                            int sizeList = tmpListTwo.size();

                            tmpListTwo = tmpListTwo.subList(l * bigBlock, tmpListTwo.size());
                            for (int s = 0; s < ((l + 1) * bigBlock - sizeList); s++) {
                                tmpListTwo.add("XX");
                            }
                        }

                        // generate the integer which is associated to free[b]

                        byte[] randomBytes = CryptoPrimitives
                                .randomBytes((int) Math.ceil((Math.log(free.size()) / (Math.log(2) * 8))));

                        int position = CryptoPrimitives.getIntFromByte(randomBytes,
                                (int) Math.ceil(Math.log(free.size()) / Math.log(2)));

                        while (position >= free.size()) {
                            position = position / 2;
                        }

                        int tmpPos = free.get(position);
                        random.nextBytes(iv);

                        byte[] list = CryptoPrimitives.encryptAES_CTR_String(key2, iv,
                                tmpListTwo.toString(), bigBlock * sizeOfFileIdentifer);

                        byte[] v = CryptoPrimitives.randomBytes(32);
                        listArrayIndexTwo.add(new String(v));
                        //TODO save aeach entry with a random key and place them in the list listArrayIndexTwo
						String id = new String(Base64.getEncoder().encode(v));
						listArrayIndexTwo.add(id);
						gamma.put(id,list);
                    }

                    // Pad the second set of identifiers

                    byte[] l = CryptoPrimitives.generateHmac(key1, Integer.toString(0));
                    random.nextBytes(iv);
                    byte[] v = CryptoPrimitives.encryptAES_CTR_String(key2, iv,
                            "3 " + listArrayIndexTwo.toString(), smallBlock * sizeOfFileIdentifer);
                    gamma.put(new String(l), v);
                }
            }
        }
        long endTime = System.nanoTime();
        long totalTime = endTime - startTime;
        // Printer.debugln("Time for one (w, id) "+totalTime/lookup.size());
        return gamma;
    }

    public static List<String> altQuery(byte[][] keys, String table)
            throws IOException, InvalidAlgorithmParameterException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, NoSuchProviderException {

        boolean found = false;
        int counter = 0;

        SetUniqueList toFetch = SetUniqueList.decorate(new LinkedList<String>());
		List<String> ret = new ArrayList<>();
        toFetch.add(new String(CryptoPrimitives.generateHmac(keys[0], Integer.toString(0))));
        do {

            List<byte[]> tempList = new ArrayList<>();

            String fetch = "SELECT IDENTIFIER FROM CLUSION." + table + " WHERE HMAC LIKE ?";
            try (PreparedStatement statement = DatabaseConnection.getInstance().prepareStatement(fetch)) {
                statement.setString(1, (String) toFetch.remove(0));
                ResultSet result = statement.executeQuery();
                if (result.next()) {
                    tempList = List.of(Base64.getDecoder().decode(result.getBytes(1)));
                }

            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            if (!(tempList.size() == 0)) {
                String temp = (new String(CryptoPrimitives.decryptAES_CTR_String(tempList.get(0), keys[1])))
                        .split("\t\t\t")[0];
                temp = temp.replaceAll("\\s", "");
				temp = temp.replaceAll(",XX", "");
                temp = temp.replace('[', ',');
                temp = temp.replace("]", "");

                String[] result = temp.split(",");

                List<String> resultFinal = new ArrayList<String>(Arrays.asList(result));
                // We remove the flag that identifies the size of the dataset

                if (result[0].equals("1")) {

                    resultFinal.remove(0);
					ret.addAll(resultFinal);
                } else if (result[0].equals("2")) {

                    toFetch.addAll(resultFinal);
                } else if (result[0].equals("3")) {

                    toFetch.addAll(resultFinal);
                }
            }

        } while (!toFetch.isEmpty());

        return ret;
    }
}
