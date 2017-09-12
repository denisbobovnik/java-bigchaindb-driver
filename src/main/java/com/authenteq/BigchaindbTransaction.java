package com.authenteq;

import com.authenteq.util.DriverUtils;
import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.jcajce.provider.digest.SHA3;
import org.interledger.cryptoconditions.types.Ed25519Sha256Condition;
import org.interledger.cryptoconditions.types.Ed25519Sha256Fulfillment;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.*;

public class BigchaindbTransaction {
    private EdDSAPublicKey publicKey;
    private JSONObject data;
    private JSONObject metadata;

    private JSONObject transactionJson;
    private boolean signed;

    /**
     * Create a BigchainDB transaction with specified data and metadata
     * @param data Payload of the transaction, defined as the asset to store
     * @param metadata Metadata contains information about the transaction itself
     *                (can be `null` if not needed)
     * @param publicKey
     */
    public BigchaindbTransaction(JSONObject data, JSONObject metadata, EdDSAPublicKey publicKey) {
        this.publicKey = publicKey;
        this.data = DriverUtils.makeSelfSorting(data);
        this.metadata = DriverUtils.makeSelfSorting(metadata);
        buildTransactionJson();
    }

    /**
     * Builds the transaction JSON without actually signing it
     */
    protected void buildTransactionJson() {
        JSONObject asset = DriverUtils.getSelfSortingJson();
        JSONObject outputs = DriverUtils.getSelfSortingJson();
        JSONObject inputs = DriverUtils.getSelfSortingJson();
        JSONObject condition = DriverUtils.getSelfSortingJson();
        JSONObject details = DriverUtils.getSelfSortingJson();
        JSONArray inputsArr = new JSONArray();
        JSONArray outputsArr = new JSONArray();

        Ed25519Sha256Condition condition1 = new Ed25519Sha256Condition(
                publicKey);

        JSONObject rootObject = DriverUtils.getSelfSortingJson();
        try {
            if (metadata == null) {
                rootObject.put("metadata", JSONObject.NULL);
            }
            else {
                rootObject.put("metadata", metadata);
            }

            rootObject.put("operation", "CREATE");
            rootObject.put("version", "1.0");
            asset.put("data", data);
            rootObject.put("asset", asset);

            outputs.put("amount", "1");
            JSONArray publicKeys = new JSONArray();
            publicKeys.put(DriverUtils.convertToBase58(publicKey));
            outputs.put("public_keys", publicKeys);
            outputsArr.put(outputs);
            rootObject.put("outputs", outputsArr);

            condition.put("uri", condition1.getUri().toString());

            details.put("public_key", DriverUtils.convertToBase58(publicKey));
            details.put("type", "ed25519-sha-256");
            condition.put("details", details);
            outputs.put("condition", condition);

            inputs.put("fulfillment", JSONObject.NULL);
            inputs.put("fulfills", JSONObject.NULL);
            JSONArray ownersBefore = new JSONArray();
            ownersBefore.put(DriverUtils.convertToBase58(publicKey));
            inputs.put("owners_before", ownersBefore);
            inputsArr.put(inputs);
            rootObject.put("inputs", inputsArr);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        transactionJson = rootObject;
    }

    /**
     * Sign the transaction with the specified private key
     * @param privateKey
     * @throws InvalidKeyException
     * @throws SignatureException
     */
    public void signTransaction(EdDSAPrivateKey privateKey) throws InvalidKeyException, SignatureException {
        try {
            // getting SHA3 hash of the current JSON object
            SHA3.DigestSHA3 md = new SHA3.DigestSHA3(256);
            md.update(transactionJson.toString().getBytes());
            String id = DriverUtils.getHex(md.digest());

            // putting the hash as id field
            transactionJson.put("id", id);

            // signing the transaction
            Signature edDsaSigner = new EdDSAEngine(MessageDigest.getInstance("SHA-512"));
            edDsaSigner.initSign(privateKey);
            edDsaSigner.update(transactionJson.toString().getBytes());
            byte[] signature = edDsaSigner.sign();
            Ed25519Sha256Fulfillment fulfillment
                    = new Ed25519Sha256Fulfillment(publicKey, signature);
            JSONObject inputs = transactionJson.getJSONArray("inputs").getJSONObject(0);
            inputs.put("fulfillment",
                    Base64.encodeBase64URLSafeString(fulfillment.getEncoded()));
            signed = true;
        }
        catch (JSONException|NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    /**
     * @return Whether the transaction is successfully signed
     */
    public boolean isSigned() {
        return signed;
    }

    /**
     * @return The JSON representation of the transaction
     */
    public JSONObject getTransactionJson() {
        return transactionJson;
    }
}