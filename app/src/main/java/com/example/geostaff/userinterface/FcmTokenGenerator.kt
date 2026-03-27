object FcmTokenGenerator {

    // ✅ YOUR EMAIL
    private const val CLIENT_EMAIL = "firebase-adminsdk-fbsvc@geostaff-b0d66.iam.gserviceaccount.com"

    // ✅ YOUR PRIVATE KEY (Copied from your file)
    // Note: I kept the \n characters as they are required for the code to clean it properly.
    private const val PRIVATE_KEY_STRING = "-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCZsgMTg4THHI+Y\nIgds0nLoOrVcXlpXDaArsrQZVpCNanpd7QzgCZxxprfK8vQZy7p2Qx+cCbBHmPvX\nf/vWKuFYWgBaekC/IOjzabi4axyfUarjEWLolBenOfF22WoSakPXKWkHZ6v32V/D\ncyAMT94CIlznzLoQQlBkboE/UwZ9cgo3IkH5RceiNmfyxKNI9mJs1UunkkGjIMHj\nADmfCWVQ28Nv+hdDkic7XQdGxWX387glzN+8N/tyGP8w7IiF1o1cHoBQUarjuSiI\nK7f7tNTZOEIeCs6a8HQIwJFUJ7brZjxfP0Gg1/1TnEcdKJSxW7XN/Ddi/gZ87mGR\n9raF9N+RAgMBAAECggEAJdEos5z5RzrOVDsPQ6X2kxCa/lrS9LeMESR77v4fPESW\nTlUdBWUoAsjoUT39dPltbFrwxKaXos5QLUK29wf/AYvHqXuKQdz5pKb/RhVI6iSg\nnRVyllKWDVYTBVDSqixOe/sa9jD6ndX1G7TQjmb6c+D7pid4IrcGa+fK0od9wdCz\nIbJJeb6aMVPOnZ52sfV+L5ReWG12+bQ9ny3BxFhdy8N1DCyH6pTV9/aKFGqu60zC\nD5DkgzokZ7SVxjz2Ttdcc3mE5bhwWkN3ACOMZJJPlDFUvSd0yZtr+alhf5bN5+eO\nQswcpp5FxS476ZS3a33GfOcxPRGfGf4J5jCWbdOo/wKBgQDXmDyoHPrPM8ag+i07\n0gvXBuub11qCvEFlSfzjQYnYYHULt0cCgxzibC82UYuLlQpyZEpHMGpaqudVTUI9\n837HGWutz6uA8kTf6wEFnfQbfhuWsx+99i1iWIj+0IkrsaNZxeYLdCOn20GwdL9y\nygn7ib0MufoknZwmS8iuTS9cEwKBgQC2f/sN0otIceXBubMP66E0mQfwTYKRgNRW\nxc7ALn0kpqfXhJpYe5QM8FGjebHZuElmlMwvduSM1T5zny69Wr6IImyVUIyKJz5F\nZcK2POlCevOh8LKg7UcgwLrYFsGg9LIUDa+dXpgmExuiAPpYbLOwBCF6LSQbWQMw\nc41YukhCSwKBgQC5Z8qCKKnolvYyafOMhk54VRM5qjHETHFQ0hgQt8P7uLfvo2YF\n2wt0DOVtKSOAnmOljGn0XsaXwnG/Afn9nPFh95KKnxU2hyKEDm2KjxPmsMS5DMI3\ncwYElW769AuC7/kysuXq463mJMCRJ1WdOfLrxsA1uZpM8t3ecT3b57Ta0QKBgQCM\n/cyv/hPa8RtmkheTWh/dqchnTwprbNMfAozbDk9iLFqI9wnjB/32DQeVOyQ6ptQW\nKyigFsM1Rl2MJ9ONOHjwZV/r7yHUbcL/DEHkVqSC1sg4OONXzlhgJmVzIgmPVtvp\nADXHZ1g2OhbkDstZ2wjrGBXg9NTrR9Zd7Teq76d5hwKBgEmsSgJIkY9bciZQ9A3X\n/M4CFBGH4obvdOMxSd8WR4y2gTIEQTSE3rijTC9PYZrk+UD4giNCINH3tOuw/epk\nFPZW0BRzJDwqRAMsxWTrnrAsXGkQqkJzhzemAokCcrujhfl1iea6KZS5EgC1QO5g\nZs++sP3pxn3b3aIlL9gwB6UG\n-----END PRIVATE KEY-----\n"

    fun getAccessToken(): String? {
        try {
            val now = System.currentTimeMillis()
            // The replace calls clean up the string to make it a valid Java Key
            val cleanKey = PRIVATE_KEY_STRING
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\n", "")
                .replace("\n", "")
                .replace(" ", "")

            val keyBytes = java.util.Base64.getDecoder().decode(cleanKey)
            val keySpec = java.security.spec.PKCS8EncodedKeySpec(keyBytes)
            val privateKey = java.security.KeyFactory.getInstance("RSA").generatePrivate(keySpec)

            return io.jsonwebtoken.Jwts.builder()
                .setIssuer(CLIENT_EMAIL)
                .setAudience("https://oauth2.googleapis.com/token")
                .setIssuedAt(java.util.Date(now))
                .setExpiration(java.util.Date(now + 3600 * 1000)) // 1 hour
                .claim("scope", "https://www.googleapis.com/auth/firebase.messaging")
                .signWith(io.jsonwebtoken.SignatureAlgorithm.RS256, privateKey)
                .compact()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}