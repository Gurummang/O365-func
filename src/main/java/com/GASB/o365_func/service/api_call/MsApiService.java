package com.GASB.o365_func.service.api_call;

import com.GASB.o365_func.model.entity.WorkspaceConfig;
import com.GASB.o365_func.repository.MonitoredUsersRepo;
import com.GASB.o365_func.repository.WorkSpaceConfigRepo;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.Site;
import com.microsoft.graph.models.User;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.DriveItemCollectionPage;
import com.microsoft.graph.requests.SiteCollectionPage;
import com.microsoft.graph.requests.UserCollectionPage;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.jni.FileInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.Key;
import java.util.*;

@Service
@Slf4j
@ConfigurationProperties(prefix = "onedrive")
public class MsApiService {

    // 흠 굳이 토큰값을 저장할 필요없이 주입받는게 나으려나?
    private String accessToken;
    private static final String SCOPES = "https://graph.microsoft.com/.default";

    @Value("{jwt.secret}")
    private String JWT_SECRET;

    private final MonitoredUsersRepo monitoredUsersRepo;
    private final SimpleAuthProvider simpleAuthProvider;
    private final WorkSpaceConfigRepo workspaceConfigRepo;
    @Autowired
    public MsApiService(MonitoredUsersRepo monitoredUsersRepo, SimpleAuthProvider simpleAuthProvider, WorkSpaceConfigRepo workspaceConfigRepo
                        /*@Value("${onedrive.client.id}") String clientId,
                        @Value("${onedrive.client.secret}") String clientSecret,
                        @Value("${onedrive.tenant.id}") String tenantId*/) {
        this.simpleAuthProvider = simpleAuthProvider;
        this.monitoredUsersRepo = monitoredUsersRepo;
        this.workspaceConfigRepo = workspaceConfigRepo;

//        // ClientSecretCredential을 사용하여 자격 증명 생성
//        ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder()
//                .clientId(clientId)
//                .clientSecret(clientSecret)
//                .tenantId(tenantId)
//                .build();
//
//        TokenCredentialAuthProvider tokenCredentialAuthProvider = new TokenCredentialAuthProvider(Collections.singletonList(SCOPES), clientSecretCredential);
//        TokenRequestContext requestContext = new TokenRequestContext().addScopes(SCOPES);
//
//        accessToken = Objects.requireNonNull(clientSecretCredential.getToken(requestContext).block()).getToken();
//        log.info("Access token: {}", accessToken);
//        this.graphClient = GraphServiceClient
//                .builder()
//                .authenticationProvider(tokenCredentialAuthProvider)
//                .buildClient();

    }


    public GraphServiceClient createGraphClient(int workspace_id){
        WorkspaceConfig workspaceConfig = workspaceConfigRepo.findById(workspace_id)
                .orElseThrow(() -> new RuntimeException("Workspace not found for id: " + workspace_id));

        String token = workspaceConfig.getToken();
        if (token == null) {
            log.error("Token is null");
            return null;
        }
        if (!tokenValidation(token)) {
            log.error("Token is invalid");
            return null;
        }
        simpleAuthProvider.setAccessToken(token);
        return GraphServiceClient
                .builder()
                .authenticationProvider(simpleAuthProvider)
                .buildClient();
    }


    public UserCollectionPage fetchUsersList(GraphServiceClient graphClient){
        return graphClient.users()
                .buildRequest()
                .select("id,displayName,mail")
                .get();
    }

    // List files
    public List<DriveItemCollectionPage> fetchFileLists(GraphServiceClient graphClient) {
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

    public List<SiteCollectionPage> fetchSiteLists(GraphServiceClient graphClient) {
        List<SiteCollectionPage> responses = new ArrayList<>();
        try {
            log.info("Fetching SharePoint site lists...");

            // 쉐어포인트 사이트 리스트 가져오기
            SiteCollectionPage siteItems = graphClient.sites()
                    .buildRequest()
                    .get();

            responses.add(siteItems);
            log.info("Fetched {} SharePoint sites.", siteItems.getCurrentPage().size());

            // 각 사이트의 정보 로깅
//            siteItems.getCurrentPage().forEach(site ->
//                    log.info("Site ID: {}, Name: {}, URL: {}", site.id, site.displayName, site.webUrl)
//            );

        } catch (GraphServiceException e) {
            log.error("GraphServiceException occurred while fetching SharePoint sites: {}", e.getMessage(), e);
            if (e.getResponseCode() == 404) {
                log.error("No SharePoint sites found: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("Unexpected error occurred while fetching SharePoint sites: {}", e.getMessage(), e);
        }
        return responses;
    }

    public List<DriveItemCollectionPage> fetchFileListsInSite(GraphServiceClient graphClient, List<SiteCollectionPage> siteList) {
        List<DriveItemCollectionPage> responses = new ArrayList<>();
        for (SiteCollectionPage sitePage : siteList) {
            for (Site site : sitePage.getCurrentPage()) {
                try {
                    log.info("Fetching files for site ID: {}", site.id);

                    // 각 사이트의 루트 드라이브에서 파일 가져오기
                    DriveItemCollectionPage driveItems = graphClient.sites(site.id)
                            .drive()
                            .root()
                            .children()
                            .buildRequest()
                            .get();

                    responses.add(driveItems);
//
//                    // 각 파일의 정보 로깅
//                    driveItems.getCurrentPage().forEach(file ->
//                            log.info("File ID: {}, Name: {}, Size: {}", file.id, file.name, file.size)
//                    );

                } catch (GraphServiceException e) {
                    log.error("GraphServiceException occurred while fetching files for site ID {}: {}", site.id, e.getMessage(), e);
                } catch (Exception e) {
                    log.error("Unexpected error occurred while fetching files for site ID {}: {}", site.id, e.getMessage(), e);
                }
            }
        }
        return responses;
    }

    //토큰 검증하는 부분
    private boolean tokenValidation(String token) {
        // o365는 토큰이 jwt형식이다, jwt를 검증하는 로직을 작성해야한다.
        Claims claims = extractAllClaims(token);
        Date exp = claims.getExpiration();
        if (exp.before(new Date())) {
            log.error("Token is expired");
            // 갱신 코드 추가
            return false;
        }
        return true;
    }

    // 토큰을 파싱하는 부분
    public Claims extractAllClaims(String token) {
        Key key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}