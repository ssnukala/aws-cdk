package com.ppkn.cdk.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.Objects;

@Slf4j
public class KeystoreHandler {

    private final String keyStoreLocation, storePassword, keyPassword;
    private KeyStore ks = null;

    public KeystoreHandler(final String keyStoreLocation, final String storePassword, final String keyPassword) {
        this.keyStoreLocation = keyStoreLocation;
        this.storePassword = storePassword;
        this.keyPassword = keyPassword;
        loadKeystore();
    }
    public void loadKeystore()  {
        try {
            Objects.requireNonNull(keyStoreLocation, "keyStoreLocation cannot be null. Please check");
            Objects.requireNonNull(storePassword, "storePassword cannot be empty");
            Objects.requireNonNull(keyPassword, "keyPassword cannot be empty");

            ks = KeyStore.getInstance("PKCS12");
            ks.load(new FileInputStream(keyStoreLocation), storePassword.toCharArray());
            log.info("Successfully loaded keystore..");
        }catch (Exception e) {
            throw new RuntimeException("Unable to load keystore. Please check keystore location set in environment variable ");
        }
    }

    public String getValueFromKeystore(String entry) {
        try {
            Objects.requireNonNull(entry, "Entry cannot be null");
            KeyStore.PasswordProtection keyStorePP = new KeyStore.PasswordProtection(keyPassword.toCharArray());
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBE");
            KeyStore.SecretKeyEntry ske =
                (KeyStore.SecretKeyEntry)ks.getEntry(entry, keyStorePP);
            PBEKeySpec keySpec = (PBEKeySpec)factory.getKeySpec(
                ske.getSecretKey(),
                PBEKeySpec.class);
            char[] password = keySpec.getPassword();
            return new String(password);
        }catch (Exception e) {
            throw new RuntimeException("Exception while retrieving the entry : " + entry, e);
        }
    }
}
