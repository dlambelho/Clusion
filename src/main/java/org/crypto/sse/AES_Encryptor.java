package org.crypto.sse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AES_Encryptor {


    private final Cipher encryptCipher;
    private final Cipher decryptCipher;
    private final byte[] ivBytes;

    {
        try {
            encryptCipher = Cipher.getInstance("AES/CTR/NoPadding", "BC");
            decryptCipher = Cipher.getInstance("AES/CTR/NoPadding", "BC");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (NoSuchProviderException e) {
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    public AES_Encryptor(byte[] keyBytes, byte[] ivBytes)
            throws InvalidAlgorithmParameterException, InvalidKeyException {
        this.ivBytes = ivBytes.clone();
        IvParameterSpec ivSpec = new IvParameterSpec(this.ivBytes.clone());
        SecretKeySpec key = new SecretKeySpec(keyBytes.clone(), "AES");

        encryptCipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);

        decryptCipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
    }

    public byte[] encryptAES_CTR_String(String identifier, int sizeOfFileName)
            throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException,
            NoSuchProviderException, NoSuchPaddingException, IOException {

        // Concatenate the title with the text. The title should be at most
        // "sizeOfFileName" characters including 3 characters marking the end of
        // it
        identifier = identifier + "\t\t\t";
        byte[] input = concat(identifier.getBytes(), new byte[sizeOfFileName - identifier.getBytes().length]);

        ByteArrayInputStream bIn = new ByteArrayInputStream(input);
        CipherInputStream cIn = new CipherInputStream(bIn, encryptCipher);
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();

        int ch;
        while ((ch = cIn.read()) >= 0) {
            bOut.write(ch);
        }
        byte[] cipherText = concat(ivBytes, bOut.toByteArray());

        cIn.close();

        return cipherText;

    }

    public byte[] decryptAES_CTR_String(byte[] input, byte[] keyBytes)
            throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException,
            NoSuchProviderException, NoSuchPaddingException, IOException {

        byte[] ivBytes = new byte[16];

        byte[] cipherText = new byte[input.length - 16];

        System.arraycopy(input, 0, ivBytes, 0, ivBytes.length);
        System.arraycopy(input, ivBytes.length, cipherText, 0, cipherText.length);

        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        CipherOutputStream cOut = new CipherOutputStream(bOut, decryptCipher);

        cOut.write(cipherText);
        cOut.close();

        return bOut.toByteArray();
    }

    public static byte[] concat(byte[] a, byte[] b) {
        int aLen = a.length;
        int bLen = b.length;
        byte[] c = new byte[aLen + bLen];
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);
        return c;
    }


}
