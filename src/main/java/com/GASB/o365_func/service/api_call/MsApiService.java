package com.GASB.o365_func.service.api_call;

import com.GASB.o365_func.model.dto.MsFileInfoDto;
import com.GASB.o365_func.model.entity.MonitoredUsers;
import com.GASB.o365_func.model.entity.MsDeltaLink;
import com.GASB.o365_func.repository.MonitoredUsersRepo;
import com.GASB.o365_func.repository.MsDeltaLinkRepo;
import com.GASB.o365_func.repository.WorkSpaceConfigRepo;
import com.GASB.o365_func.service.util.JwtDecoder;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.DriveItemDeltaParameterSet;
import com.microsoft.graph.models.Site;
import com.microsoft.graph.requests.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

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
    private final MsDeltaLinkRepo msDeltaLinkRepo;

    private GraphServiceClient<?> graphClient;
    @Autowired
    public MsApiService(MonitoredUsersRepo monitoredUsersRepo, SimpleAuthProvider simpleAuthProvider, WorkSpaceConfigRepo workspaceConfigRepo, MsDeltaLinkRepo msDeltaLinkRepo
                        /*@Value("${onedrive.client.id}") String clientId,
                        @Value("${onedrive.client.secret}") String clientSecret,
                        @Value("${onedrive.tenant.id}") String tenantId*/) {
        this.simpleAuthProvider = simpleAuthProvider;
        this.monitoredUsersRepo = monitoredUsersRepo;
        this.workspaceConfigRepo = workspaceConfigRepo;
        this.msDeltaLinkRepo = msDeltaLinkRepo;
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


    public GraphServiceClient<?> createGraphClient(int workspace_id){
        if (graphClient == null) {
            String token = workspaceConfigRepo.findTokenById(workspace_id).orElse(null);
            log.info("Token: {}",token);
            if (token == null || !tokenValidation(token)) {
                log.error("Invalid or expired token for workspace {}", workspace_id);
                return null;
            }
            simpleAuthProvider.setAccessToken(token);
            graphClient = GraphServiceClient.builder().authenticationProvider(simpleAuthProvider).buildClient();
        }
        return graphClient;
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

    public DriveItem getEachFileInto(String userId, String fileId, GraphServiceClient graphClient) {
        try {
            DriveItem driveItem = graphClient.users(userId)
                    .drive()
                    .items(fileId)
                    .buildRequest()
                    .get();
            return driveItem;
        } catch (GraphServiceException e) {
            log.error("GraphServiceException occurred while fetching file for user ID {} and file ID {}: {}", userId, fileId, e.getMessage(), e);
            return null;
        } catch (Exception e) {
            log.error("Unexpected error occurred while fetching file for user ID {} and file ID {}: {}", userId, fileId, e.getMessage(), e);
            return null;
        }
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


    /**
     * 최초 Delta API 호출: 특정 사용자의 OneDrive에서 변경 사항 추적 시작
     * @param userId 사용자 ID
     * @return DriveItemDeltaCollectionPage 최초 Delta API 호출 결과
     */
    public String initDeltaLink(String userId, GraphServiceClient<?> graphClient) {
        try {
            int workspace_id = monitoredUsersRepo.getOrgSaaSId(userId);
            // 최초 Delta API 호출 (OneDrive 루트 디렉터리)
            DriveItemDeltaCollectionPage deltaPage = graphClient
                    .users(userId)
                    .drive()
                    .root()
                    .delta()  // delta() 메서드가 최초 Delta 호출을 의미
                    .buildRequest()
                    .get();

            // 반환된 Delta 데이터 및 deltaLink 처리
            String deltaLink = deltaPage.deltaLink;
            log.info("Initial deltaLink for user {}: {}", userId, deltaLink);

            // DeltaLink 저장
            // DB구조 수정 이후 추가 예정
            saveDeltaLink(userId, deltaLink);

//            fetchDeltaChanges(userId, deltaLink,graphClient);
            return deltaLink;
            // Delta 결과 반환
        } catch (Exception e) {
            log.error("Error occurred while initiating Delta API for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    private void saveDeltaLink(String userId, String deltaLink) {
        MonitoredUsers monitoredUsers = monitoredUsersRepo.findByUserId(userId).orElse(null);
        if (monitoredUsers == null) {
            log.error("User not found for user ID: {}", userId);
            return;
        }
        String token = deltaLink.split("token=")[1];
        if (msDeltaLinkRepo.existsByMonitoredUsers_Id(monitoredUsers.getId())){
            msDeltaLinkRepo.updateDeltaLink(token, monitoredUsers.getId());
            return;
        }
        MsDeltaLink msDeltaLink = MsDeltaLink.builder()
                .monitoredUsers(monitoredUsers)
                .deltaLink(token)
                .build();
        msDeltaLinkRepo.save(msDeltaLink);
    }

    public DriveItemDeltaCollectionPage fetchDeltaChangesItem(String userId,String deltaLink, GraphServiceClient<?> graphClient) {
        try {
            DriveItemDeltaParameterSet parameterSet = DriveItemDeltaParameterSet
                    .newBuilder()
                    .withToken(deltaLink)  // DeltaLink 전달
                    .build();

            DriveItemDeltaCollectionPage deltaPage = graphClient
                    .users(userId)
                    .drive()
                    .root()
                    .delta(parameterSet)
                    .buildRequest()
                    .get();

            return deltaPage;
        } catch (Exception e) {
            log.error("Error occurred while fetching delta changes for user {}: {}", userId, e.getMessage());
            return null;
        }
    }


    public CompletableFuture<Map<DriveItem, String>> fetchDeltaInfo(String userId, GraphServiceClient<?> graphClient) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<DriveItem, String> response = new HashMap<>();

                // DeltaLink 조회
                int user_id = monitoredUsersRepo.getIdx(userId);
                String deltaLink = msDeltaLinkRepo.findDeltaLinkByUserId(user_id).orElse(null);
                log.info("DeltaLink for user {}: {}", userId, deltaLink);

                DriveItemDeltaCollectionPage deltaPage = fetchDeltaChangesItem(userId, deltaLink, graphClient);
                log.info("DriveItemDeltaCollectionPage: {}", deltaPage.getCurrentPage().size());
                log.info("Page : {}", deltaPage.getCurrentPage());

                // 변경 사항이 없는 경우
                if (deltaPage.getCurrentPage().isEmpty()) {
                    log.info("No changes found for user {}", userId);
                }

                deltaPage.getCurrentPage().forEach(driveItem -> {
                    log.info("File ID: {}, Name: {}, Size: {}, ", driveItem.id, driveItem.name, driveItem.size);
//                    DriveItem item = getEachFileInto(userId, driveItem.id, graphClient);
                    if (driveItem.folder != null){
                        log.info("Folder: {}", driveItem.folder);
                        return;
                    }
                    String eventType = eventTypeSeperator(driveItem);
                    switch (eventType) {
                        case "file_delete" -> response.put(driveItem, "file_delete");
                        case "file_change" -> response.put(getEachFileInto(userId, driveItem.id, graphClient), "file_change");
                        case "file_upload" -> response.put(getEachFileInto(userId, driveItem.id, graphClient), "file_upload");
                    }
//                    response.put(item, eventTypeSeperator(driveItem)); // 근데 지금 이벤트 타입의 경우에 따라서 다르게 처리해야함. delete의 경우에는 이게 안오기 때문에...
                });

                // 새롭게 deltaLink를 업데이트
                initDeltaLink(userId, graphClient); //여기까지는 잘 되는것을 확인!
                return response;

            } catch (Exception e) {
                log.error("Error occurred while fetching delta changes for user {}: {}", userId, e.getMessage());
                return Collections.emptyMap(); // 예외 시 빈 리스트 반환
            }
        });
    }

    private String eventTypeSeperator(DriveItem item) {
        if (item.deleted != null) {
            return "file_delete";
        } else if (item.createdDateTime != null && item.lastModifiedDateTime != null) {
            if (item.createdDateTime.equals(item.lastModifiedDateTime)) {
                return "file_upload";
            } else {
                return "file_change";
            }
        }
        return "";
    }


    //토큰 검증하는 부분
    private boolean tokenValidation(String token) {
        // o365는 토큰이 JWT 형식이다, JWT를 검증하는 로직을 작성해야 한다.
        String decodedPayload = JwtDecoder.decodeJwtPayload(token);
        Date expDate = JwtDecoder.getExpDate(token); // 페이로드가 아닌 전체 JWT를 전달
        log.info("Decoded payload: {}", decodedPayload);

        // 토큰 만료일이 현재 시간보다 이전이면 false
        if (expDate.before(new Date())) {
            log.error("Token is expired");
            return false;
        }

        // 추가적인 검증 로직이 필요하다면 여기에 추가
        // 예: 서명 검증, issuer 검증 등

        return true;
    }

    public boolean MsFileDeleteApi(int workspace_id, String itemId) {
        try {
            GraphServiceClient<?> graphServiceClient = createGraphClient(workspace_id);
            // 드라이브와 아이템 ID를 사용하여 파일 삭제 요청 생성
            CompletableFuture<DriveItem> future = graphServiceClient
                    .me()
                    .drive()
                    .items(itemId)
                    .buildRequest()
                    .deleteAsync();

            // 요청 완료 대기
            future.join();
            log.info("File deleted successfully from OneDrive: " + itemId);
            return true;

        } catch (ClientException e) {
            // Microsoft Graph API 에러 처리
            log.info("Error occurred while deleting file: " + e.getMessage());
            return false;
        }
    }
}