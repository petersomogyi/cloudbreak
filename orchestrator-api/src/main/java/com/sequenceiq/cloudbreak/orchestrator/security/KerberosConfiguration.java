package com.sequenceiq.cloudbreak.orchestrator.security;

public final class KerberosConfiguration {

    private final String masterKey;

    private final String user;

    private final String password;

    public KerberosConfiguration(String masterKey, String user, String password) {
        this.masterKey = masterKey;
        this.user = user;
        this.password = password;
    }

    public String getMasterKey() {
        return masterKey;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }
}
