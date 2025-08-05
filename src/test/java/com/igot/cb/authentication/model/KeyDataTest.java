package com.igot.cb.authentication.model;

import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KeyDataTest {

    /**
     * Tests the KeyData constructor with valid input parameters.
     * Verifies that the keyId and publicKey are correctly set.
     */
    @Test
    void test_KeyData_Constructor_WithValidParameters() throws NoSuchAlgorithmException {
        String keyId = "testKeyId";
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        PublicKey publicKey = keyGen.generateKeyPair().getPublic();

        KeyData keyData = new KeyData(keyId, publicKey);

        assertEquals(keyId, keyData.getKeyId());
        assertEquals(publicKey, keyData.getPublicKey());
    }

    /**
     * Test case for the getKeyId method of the KeyData class.
     * This test verifies that the getKeyId method correctly returns the keyId
     * that was set during object initialization.
     */
    @Test
    void test_getKeyId_returnsCorrectKeyId() throws NoSuchAlgorithmException {
        // Arrange
        String expectedKeyId = "testKeyId";
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        PublicKey publicKey = keyGen.generateKeyPair().getPublic();
        KeyData keyData = new KeyData(expectedKeyId, publicKey);

        // Act
        String actualKeyId = keyData.getKeyId();

        // Assert
        assertEquals(expectedKeyId, actualKeyId);
    }

    /**
     * Test getKeyId when keyId is null.
     * This test verifies that the getKeyId method returns null when the keyId field is null.
     */
    @Test
    void test_getKeyId_returns_null() {
        KeyData keyData = new KeyData(null, null);
        assertNull(keyData.getKeyId());
    }

    /**
     * Test case for getPublicKey method of KeyData class.
     * Verifies that the method correctly returns the public key set during object creation.
     */
    @Test
    void test_getPublicKey_returnsCorrectPublicKey() throws NoSuchAlgorithmException {
        // Generate a public key for testing
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        PublicKey expectedPublicKey = keyGen.generateKeyPair().getPublic();

        // Create a KeyData object with the generated public key
        KeyData keyData = new KeyData("testKeyId", expectedPublicKey);

        // Call the getPublicKey method
        PublicKey actualPublicKey = keyData.getPublicKey();

        // Assert that the returned public key matches the one set during object creation
        assertEquals(expectedPublicKey, actualPublicKey);
    }

    /**
     * Tests that getPublicKey returns null when the publicKey field is null.
     * This tests the edge case where the KeyData object was initialized with a null publicKey.
     */
    @Test
    void test_getPublicKey_returnsNull_whenPublicKeyIsNull() {
        KeyData keyData = new KeyData("testId", null);
        PublicKey result = keyData.getPublicKey();
        assertNull(result);
    }

    /**
     * Test case for setKeyId method of KeyData class
     * Verifies that the keyId is correctly set and can be retrieved
     */
    @Test
    void test_setKeyId_1() {
        KeyData keyData = new KeyData(null, null);
        String expectedKeyId = "testKeyId";
        keyData.setKeyId(expectedKeyId);
        assertEquals(expectedKeyId, keyData.getKeyId());
    }

    /**
     * Test that setPublicKey correctly sets the public key of a KeyData object
     */
    @Test
    void test_setPublicKey_setsPublicKeyCorrectly() throws NoSuchAlgorithmException {
        KeyData keyData = new KeyData("testId", null);
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        PublicKey publicKey = keyGen.generateKeyPair().getPublic();

        keyData.setPublicKey(publicKey);

        assertEquals(keyData.getPublicKey(), publicKey);
    }

}
