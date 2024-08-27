package com.GASB.o365_func.service.api_call;

import com.GASB.o365_func.repository.MonitoredUsersRepo;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.User;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.DriveItemCollectionPage;
import com.microsoft.graph.requests.UserCollectionPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.jni.FileInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "onedrive")
public class MsApiService {

    // 흠 굳이 토큰값을 저장할 필요없이 주입받는게 나으려나?
    private final String clientId;
    private final String clientSecret;
    private final String tenantId;
    private String accessToken;
    private static final List<String> SCOPES = List.of("https://graph.microsoft.com/.default");

    private final GraphServiceClient graphClient;
    private final MonitoredUsersRepo monitoredUsersRepo;

    @Autowired
    public MsApiService(@Value("${onedrive.client.id}") String clientId,
                        @Value("${onedrive.client.secret}") String clientSecret,
                        @Value("${onedrive.tenant.id}") String tenantId,
                        MonitoredUsersRepo monitoredUsersRepo) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tenantId = tenantId;

        ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .tenantId(tenantId)
                .build();

        TokenCredentialAuthProvider tokenCredentialAuthProvider = new TokenCredentialAuthProvider(SCOPES, clientSecretCredential);
        TokenRequestContext requestContext = new TokenRequestContext().addScopes(SCOPES.get(0));

        accessToken = Objects.requireNonNull(clientSecretCredential.getToken(requestContext).block()).getToken();
        log.info("Access token: {}", accessToken);
        this.graphClient = GraphServiceClient
                .builder()
                .authenticationProvider(tokenCredentialAuthProvider)
                .buildClient();

        this.monitoredUsersRepo = monitoredUsersRepo;
    }
    // Get user information
    public User fetchUsers(String userId) {
        return graphClient.users(userId)
                .buildRequest()
                .get();
    }

    public GraphServiceClient getGraphClient(){
        return graphClient;
    }

    public UserCollectionPage fetchUsersList(){
        return graphClient.users()
                .buildRequest()
                .select("id,displayName,mail")
                .get();
    }

    // List files
    public List<DriveItemCollectionPage> fetchFileLists() {
        List<String> userList = monitoredUsersRepo.getUserList();
        List<DriveItemCollectionPage> responses = new ArrayList<>();
        for (String user_id : userList) {
            log.info("Fetching files for user_id: {}", user_id);
            try {
                DriveItemCollectionPage driveItems = graphClient.users(user_id)
                        .drive()
                        .root()
                        .children()
                        .buildRequest()
//                        .select("createdBy,createdDateTime, id, @microsoft.graph.downloadUrl,name,size,item.parentReference")
                        .get();
                responses.add(driveItems);
            } catch (GraphServiceException e) {
                if (e.getResponseCode() == 404) {
                    log.error("Drive not found for user_id: {}", user_id);
                } else {
                    log.error("An error occurred while fetching files for user_id: {}", user_id, e);
                }
                continue;
            } catch (Exception e) {
                log.error("Unexpected error occurred for user_id: {}", user_id, e);
                continue;
            }
        }

        return responses;
    }
    public String getAccessToken(){
        return accessToken;
    }

    public String refreshAccessToken(){
        ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .tenantId(tenantId)
                .build();
        TokenRequestContext requestContext = new TokenRequestContext().addScopes(SCOPES.get(0));

        return Objects.requireNonNull(clientSecretCredential.getToken(requestContext).block()).getToken();
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