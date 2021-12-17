package com.example.swob_server;

import android.util.Log;

import java.io.IOException;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyAgreement;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class DHKeyAgreement2 {
    public DHKeyAgreement2() throws NoSuchAlgorithmException {
        this.keyAgree  = KeyAgreement.getInstance("DH");
    }

    public KeyPair keypair;
    public KeyAgreement keyAgree;

    public byte[] generateKeyEncoded() throws NoSuchAlgorithmException, InvalidKeyException {
        Log.i(this.getClass().getName(), "DHKeyAgreement: Generate DH keypair ...");
        KeyPairGenerator KpairGen = KeyPairGenerator.getInstance("DH");
        KpairGen.initialize(2048);
        this.keypair = KpairGen.generateKeyPair();

        Log.i(this.getClass().getName(), "DHKeyAgreement: Initialization ...");
        this.keyAgree.init(this.keypair.getPrivate());

        // Alice encodes her public key, and sends it over to Bob.
        byte[] alicePubKeyEnc = this.keypair.getPublic().getEncoded();

        return alicePubKeyEnc;
    }

    public byte[] generateKeyFromPublicKey(byte[] publicKeyEnc) throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidAlgorithmParameterException, InvalidKeyException {
        KeyFactory bobKeyFac = KeyFactory.getInstance("DH");
        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(publicKeyEnc);

        PublicKey publicKey = bobKeyFac.generatePublic(x509KeySpec);

        /*
         * Bob gets the DH parameters associated with Alice's public key.
         * He must use the same parameters when he generates his own key
         * pair.
         */
        DHParameterSpec dhParamFromAlicePubKey = ((DHPublicKey)publicKey).getParams();

        // Bob creates his own DH key pair
        Log.i(this.getClass().getName(), "BOB: Generate DH keypair ...");
        KeyPairGenerator KpairGen = KeyPairGenerator.getInstance("DH");
        KpairGen.initialize(dhParamFromAlicePubKey);
        this.keypair = KpairGen.generateKeyPair();

        // Bob encodes his public key, and sends it over to Alice.
        byte[] bobPubKeyEnc = this.keypair.getPublic().getEncoded();

        return bobPubKeyEnc;
    }


    public DHKeyAgreement2 DHKeyAgreement(byte[] publicKeyEnc, KeyPair keyPair) throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException {
        /*
         * Alice uses Bob's public key for the first (and only) phase
         * of her version of the DH
         * protocol.
         * Before she can do so, she has to instantiate a DH public key
         * from Bob's encoded key material.
         */
        KeyFactory keyFactory = KeyFactory.getInstance("DH");
        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(publicKeyEnc);
        PublicKey publicKey = keyFactory.generatePublic(x509KeySpec);

        this.keyAgree.init(keyPair.getPrivate());
        this.keyAgree.doPhase(publicKey, true);

        return this;
    }

    public byte[] generateSecretKey() {
        byte[] aliceSharedSecret = this.keyAgree.generateSecret();

        return aliceSharedSecret;
    }


    public byte[] encryptAES(byte[] bobSharedSecret) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, IOException {
        /*
         * Now let's create a SecretKey object using the shared secret
         * and use it for encryption. First, we generate SecretKeys for the
         * "AES" algorithm (based on the raw shared secret data) and
         * Then we use AES in CBC mode, which requires an initialization
         * vector (IV) parameter. Note that you have to use the same IV
         * for encryption and decryption: If you use a different IV for
         * decryption than you used for encryption, decryption will fail.
         *
         * If you do not specify an IV when you initialize the Cipher
         * object for encryption, the underlying implementation will generate
         * a random one, which you have to retrieve using the
         * javax.crypto.Cipher.getParameters() method, which returns an
         * instance of java.security.AlgorithmParameters. You need to transfer
         * the contents of that object (e.g., in encoded format, obtained via
         * the AlgorithmParameters.getEncoded() method) to the party who will
         * do the decryption. When initializing the Cipher for decryption,
         * the (reinstantiated) AlgorithmParameters object must be explicitly
         * passed to the Cipher.init() method.
         */
        Log.i(this.getClass().getName(), "Use shared secret as SecretKey object ...");
        SecretKeySpec bobAesKey = new SecretKeySpec(bobSharedSecret, 0, 16, "AES");

        /*
         * Bob encrypts, using AES in CBC mode
         */
        Cipher bobCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        bobCipher.init(Cipher.ENCRYPT_MODE, bobAesKey);
        byte[] cleartext = "This is just an example".getBytes();
        byte[] ciphertext = bobCipher.doFinal(cleartext);

        // Retrieve the parameter that was used, and transfer it to Alice in
        // encoded format
        byte[] encodedParams = bobCipher.getParameters().getEncoded();
        return encodedParams;
    }


    public byte[] decryptAES(byte[] encodedParams, SecretKeySpec aliceAesKey, byte[] ciphertext) throws NoSuchAlgorithmException, IOException, NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        /*
         * Alice decrypts, using AES in CBC mode
         */

        // Instantiate AlgorithmParameters object from parameter encoding
        // obtained from Bob
        AlgorithmParameters aesParams = AlgorithmParameters.getInstance("AES");
        aesParams.init(encodedParams);
        Cipher aliceCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        aliceCipher.init(Cipher.DECRYPT_MODE, aliceAesKey, aesParams);
        byte[] recovered = aliceCipher.doFinal(ciphertext);

        return recovered;
    }

    public static void test() throws Exception {
        DHKeyAgreement2 alice = new DHKeyAgreement2();
        DHKeyAgreement2 bob = new DHKeyAgreement2();

        byte[] alicePubKeyEnc = alice.generateKeyEncoded();
        byte[] bobPubKeyEnc = bob.generateKeyFromPublicKey(alicePubKeyEnc);

        byte[] aliceSharedSecret = alice.DHKeyAgreement(bobPubKeyEnc, alice.keypair)
                .generateSecretKey();
        byte[] bobSharedSecret = bob.DHKeyAgreement(alicePubKeyEnc, bob.keypair)
                .generateSecretKey();

        Log.i(DHKeyAgreement2.class.getName(), "Alice:" + aliceSharedSecret.toString());
        Log.i(DHKeyAgreement2.class.getName(), "Bob:" + bobSharedSecret.toString());

        Log.i(DHKeyAgreement2.class.getName(), "Alice secret: " +
                Helpers.toHexString(aliceSharedSecret));
        Log.i(DHKeyAgreement2.class.getName(), "Bob secret: " +
                Helpers.toHexString(bobSharedSecret));
        if (!java.util.Arrays.equals(aliceSharedSecret, bobSharedSecret))
            throw new Exception("Shared secrets differ");
        Log.i(DHKeyAgreement2.class.getName(), "Shared secrets are the same");
    }

}
