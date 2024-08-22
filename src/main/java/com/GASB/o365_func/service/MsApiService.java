package com.GASB.o365_func.service;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.models.Request;
import com.microsoft.graph.models.User;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.DriveItemCollectionPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Service
@Slf4j
public class MsApiService {

    private static final String CLIENT_ID = "your-client-id";
    private static final String CLIENT_SECRET = "your-client-secret";
    private static final String TENANT_ID = "your-tenant-id";
    private static final List<String> SCOPES = List.of("https://graph.microsoft.com/.default");

    private final GraphServiceClient graphClient;

    public MsApiService() {
        ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder()
                .clientId(CLIENT_ID)
                .clientSecret(CLIENT_SECRET)
                .tenantId(TENANT_ID)
                .build();

        TokenCredentialAuthProvider tokenCredentialAuthProvider = new TokenCredentialAuthProvider(SCOPES, clientSecretCredential);

        this.graphClient = GraphServiceClient
                .builder()
                .authenticationProvider(tokenCredentialAuthProvider)
                .buildClient();
    }

    // Get user information
    public User fetchUsers(String userId) {
        return graphClient.users(userId)
                .buildRequest()
                .get();
    }

    // List files
    public DriveItemCollectionPage fetchFileLists(String userId) {
        return graphClient.users(userId)
                .drive()
                .root()
                .children()
                .buildRequest()
                .get();
    }

    // Download file
    public byte[] downloadFile(String userId, String itemId) throws Exception {
        InputStream fileStream = graphClient.users(userId)
                .drive()
                .items(itemId)
                .content()
                .buildRequest()
                .get();
        return fileStream.readAllBytes();
    }
}