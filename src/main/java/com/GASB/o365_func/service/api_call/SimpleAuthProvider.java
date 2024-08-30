package com.GASB.o365_func.service.api_call;

import com.GASB.o365_func.repository.WorkSpaceConfigRepo;
import com.azure.identity.ClientSecretCredential;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.concurrent.CompletableFuture;

@Component
public class SimpleAuthProvider implements IAuthenticationProvider {
    private String accessToken;
    // 추후 토큰 갱신코드 또한 작성할 것.


    public void setAccessToken(String accessToken){
        this.accessToken = accessToken;
    }
    @NotNull
    @Override
    public CompletableFuture<String> getAuthorizationTokenAsync(@NotNull URL requestUrl) {
        return CompletableFuture.completedFuture(accessToken);
    }
}
