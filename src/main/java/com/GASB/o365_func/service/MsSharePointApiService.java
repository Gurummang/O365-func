package com.GASB.o365_func.service;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.models.Site;
import com.microsoft.graph.models.User;
import com.microsoft.graph.requests.DriveItemCollectionPage;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.UserCollectionPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Service
@Slf4j
public class MsSharePointApiService {

    @Value("${o365.client-id}")
    private String CLIENT_ID;
    @Value("${o365.client-secret}")
    private String CLIENT_SECRET;
    @Value("${o365.tenant-id}")
    private String TENANT_ID;
    @Value("${o365.sharepoint.site-id}")
    private List<String> SCOPES;

    private final GraphServiceClient graphClient;

    public MsSharePointApiService() {
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

    // SharePoint 사이트 정보 가져오기
    public Site getSharePointSite(String siteId) {
        log.info("Fetching SharePoint site information for siteId: {}", siteId);
        return graphClient.sites(siteId)
                .buildRequest()
                .get();
    }

    // SharePoint 문서 라이브러리 내 파일 리스팅
    public DriveItemCollectionPage listSharePointFiles(String siteId, String driveId) {
        log.info("Listing files in SharePoint document library with siteId: {} and driveId: {}", siteId, driveId);
        return graphClient.sites(siteId)
                .drives(driveId)
                .root()
                .children()
                .buildRequest()
                .get();
    }

    // SharePoint 파일 다운로드
    public byte[] downloadSharePointFile(String siteId, String driveId, String itemId) throws Exception {
        log.info("Downloading file from SharePoint with siteId: {}, driveId: {}, itemId: {}", siteId, driveId, itemId);
        InputStream fileStream = graphClient.sites(siteId)
                .drives(driveId)
                .items(itemId)
                .content()
                .buildRequest()
                .get();
        return fileStream.readAllBytes();
    }

    // SharePoint 사용자 정보 가져오기
    public User fetchSPUsers(String userId) {
        return graphClient.users(userId)
                .buildRequest()
                .get();
    }

    public UserCollectionPage fetchSPUserList() {
        log.info("Fetching list of SharePoint users");
        return graphClient.users()
                .buildRequest()
                .get();
    }

}
