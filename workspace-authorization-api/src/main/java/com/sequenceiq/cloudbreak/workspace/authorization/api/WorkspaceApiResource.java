package com.sequenceiq.cloudbreak.workspace.authorization.api;

public enum WorkspaceApiResource {
    ALL("All resources", "All"),
    WORKSPACE("Workspace", "Workspace"),
    BLUEPRINT("Blueprint", "ClusterDefinition"),
    IMAGECATALOG("Image catalog", "ImageCatalog"),
    CREDENTIAL("Credential", "Credential"),
    RECIPE("Recipe", "Recipe"),
    STACK("Stack", "Stack"),
    LDAP("LDAP config", "Ldap"),
    DATABASE("database config", "Database"),
    PROXY("Proxy config", "Proxy"),
    MPACK("MPACK resource", "Mpack"),
    KUBERNETES("Kubernetes config", "KubeConfig"),
    STRUCTURED_EVENT("Structured event resource", "StructuredEvent"),
    CLUSTER_TEMPLATE("Cluster template", "ClusterTemplate"),
    ENVIRONMENT("Environment", "Env"),
    KERBEROS_CONFIG("Kerberos Config", "Krbconf");

    private final String readableName;

    private final String authorizationName;

    WorkspaceApiResource(String readableName, String authorizationName) {
        this.readableName = readableName;
        this.authorizationName = authorizationName;
    }

    public String getReadableName() {
        return readableName;
    }

    public String getShortName() {
        return authorizationName.toLowerCase();
    }

    public String getAuthorizationName() {
        return authorizationName;
    }
}
