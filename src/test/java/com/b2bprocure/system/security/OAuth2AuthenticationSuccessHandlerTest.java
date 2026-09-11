package com.b2bprocure.system.security;

import com.b2bprocure.system.authaccount.entity.AuthAccount;
import com.b2bprocure.system.authaccount.repository.AuthAccountRepository;
import com.b2bprocure.system.common.enums.AuthProvider;
import com.b2bprocure.system.config.JwtConfig;
import com.b2bprocure.system.role.entity.Role;
import com.b2bprocure.system.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuthAccountRepository authAccountRepository;

    @Mock
    private JwtConfig jwtConfig;

    @Mock
    private OAuth2LinkStateStore oauth2LinkStateStore;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private RedirectStrategy redirectStrategy;

    @InjectMocks
    private OAuth2AuthenticationSuccessHandler successHandler;

    private static final String REDIRECT_URI = "http://localhost:3000/oauth2/redirect";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(successHandler, "redirectUri", REDIRECT_URI);
        successHandler.setRedirectStrategy(redirectStrategy);
    }

    @Test
    @DisplayName("Should generate system JWT and redirect with SUCCESS when AuthAccount exists")
    void onAuthenticationSuccess_ExistingAccount() throws Exception {
        // Arrange
        String googleSub = "google_subject_123456";
        Map<String, Object> attributes = Map.of(
                "sub", googleSub,
                "email", "buyer@gmail.com",
                "name", "Buyer User",
                "picture", "https://avatar.google.com/photo.jpg"
        );
        OAuth2User oauth2User = new DefaultOAuth2User(
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "sub"
        );
        OAuth2AuthenticationToken authToken = new OAuth2AuthenticationToken(
                oauth2User,
                oauth2User.getAuthorities(),
                "google"
        );

        Role role = new Role(2L, "BUYER", "Buyer user");
        User user = new User(
                10L, role, null, "buyer", "hashed_password",
                "Buyer User", "buyer@gmail.com", "0901234567",
                "https://avatar.google.com/photo.jpg", null, "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()
        );
        AuthAccount authAccount = AuthAccount.builder()
                .id(1L)
                .user(user)
                .provider(AuthProvider.GOOGLE)
                .providerUserId(googleSub)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(response.isCommitted()).thenReturn(false);
        when(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, googleSub))
                .thenReturn(Optional.of(authAccount));
        when(jwtTokenProvider.generateToken(any(UserPrincipal.class))).thenReturn("mocked.system.jwt.token");
        when(jwtConfig.getExpirationMs()).thenReturn(86400000L);

        // Act
        successHandler.onAuthenticationSuccess(request, response, authToken);

        // Assert
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(redirectStrategy).sendRedirect(any(), any(), urlCaptor.capture());

        String targetUrl = urlCaptor.getValue();
        assertThat(targetUrl).startsWith(REDIRECT_URI);
        assertThat(targetUrl).contains("status=SUCCESS");
        assertThat(targetUrl).contains("accessToken=mocked.system.jwt.token");
        assertThat(targetUrl).contains("tokenType=Bearer");
        assertThat(targetUrl).contains("userId=10");
        assertThat(targetUrl).contains("username=buyer");
        assertThat(targetUrl).contains("role=BUYER");
    }

    @Test
    @DisplayName("Should redirect with NEED_REGISTER status and temporary registration token when AuthAccount does not exist")
    void onAuthenticationSuccess_UnlinkedAccount() throws Exception {
        // Arrange
        String googleSub = "new_google_user_999999";
        Map<String, Object> attributes = Map.of(
                "sub", googleSub,
                "email", "newuser@gmail.com",
                "name", "New Google User",
                "picture", "https://avatar.google.com/new.jpg"
        );
        OAuth2User oauth2User = new DefaultOAuth2User(
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "sub"
        );
        OAuth2AuthenticationToken authToken = new OAuth2AuthenticationToken(
                oauth2User,
                oauth2User.getAuthorities(),
                "google"
        );

        when(response.isCommitted()).thenReturn(false);
        when(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, googleSub))
                .thenReturn(Optional.empty());
        when(jwtTokenProvider.generateRegistrationToken("GOOGLE", googleSub, "newuser@gmail.com", "New Google User"))
                .thenReturn("mocked.temporary.registration.jwt");

        // Act
        successHandler.onAuthenticationSuccess(request, response, authToken);

        // Assert
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(redirectStrategy).sendRedirect(any(), any(), urlCaptor.capture());

        String targetUrl = urlCaptor.getValue();
        assertThat(targetUrl).startsWith(REDIRECT_URI);
        assertThat(targetUrl).contains("status=NEED_REGISTER");
        assertThat(targetUrl).contains("registrationToken=mocked.temporary.registration.jwt");
        assertThat(targetUrl).doesNotContain("providerUserId=" + googleSub);
    }

    @Test
    @DisplayName("Should redirect with LINK_READY status and linkToken when pending linking state exists")
    void onAuthenticationSuccess_LinkFlow_Success() throws Exception {
        // Arrange
        String googleSub = "google_link_12345";
        String state = "oauth2_state_abc";
        Long userId = 10L;
        Map<String, Object> attributes = Map.of(
                "sub", googleSub,
                "email", "user@company.com",
                "name", "User Name",
                "picture", "https://avatar.google.com/pic.jpg"
        );
        OAuth2User oauth2User = new DefaultOAuth2User(
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "sub"
        );
        OAuth2AuthenticationToken authToken = new OAuth2AuthenticationToken(
                oauth2User,
                oauth2User.getAuthorities(),
                "google"
        );

        when(response.isCommitted()).thenReturn(false);
        when(request.getParameter("state")).thenReturn(state);
        when(oauth2LinkStateStore.getAndRemovePendingOAuth2State(state)).thenReturn(userId);
        when(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, googleSub))
                .thenReturn(Optional.empty());
        when(authAccountRepository.findByUserIdAndProvider(userId, AuthProvider.GOOGLE))
                .thenReturn(Optional.empty());
        when(jwtTokenProvider.generateLinkToken("GOOGLE", googleSub, "user@company.com", userId))
                .thenReturn("mocked.link.jwt.token");

        // Act
        successHandler.onAuthenticationSuccess(request, response, authToken);

        // Assert
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(redirectStrategy).sendRedirect(any(), any(), urlCaptor.capture());

        String targetUrl = urlCaptor.getValue();
        assertThat(targetUrl).startsWith(REDIRECT_URI);
        assertThat(targetUrl).contains("status=LINK_READY");
        assertThat(targetUrl).contains("linkToken=mocked.link.jwt.token");
        verify(oauth2LinkStateStore).recordVerifiedIdentity(userId, AuthProvider.GOOGLE, googleSub, "user@company.com");
    }

    @Test
    @DisplayName("Should redirect with ALREADY_LINKED status when Google account is already linked to the same user")
    void onAuthenticationSuccess_LinkFlow_AlreadyLinkedToSameUser() throws Exception {
        // Arrange
        String googleSub = "google_link_same_user";
        String state = "oauth2_state_same";
        Long userId = 10L;
        User user = new User();
        user.setId(userId);
        user.setUsername("same_user");
        AuthAccount existingAccount = AuthAccount.builder()
                .id(1L)
                .user(user)
                .provider(AuthProvider.GOOGLE)
                .providerUserId(googleSub)
                .build();

        Map<String, Object> attributes = Map.of(
                "sub", googleSub,
                "email", "same@company.com"
        );
        OAuth2User oauth2User = new DefaultOAuth2User(
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "sub"
        );
        OAuth2AuthenticationToken authToken = new OAuth2AuthenticationToken(
                oauth2User,
                oauth2User.getAuthorities(),
                "google"
        );

        when(response.isCommitted()).thenReturn(false);
        when(request.getParameter("state")).thenReturn(state);
        when(oauth2LinkStateStore.getAndRemovePendingOAuth2State(state)).thenReturn(userId);
        when(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, googleSub))
                .thenReturn(Optional.of(existingAccount));

        // Act
        successHandler.onAuthenticationSuccess(request, response, authToken);

        // Assert
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(redirectStrategy).sendRedirect(any(), any(), urlCaptor.capture());

        String targetUrl = urlCaptor.getValue();
        assertThat(targetUrl).startsWith(REDIRECT_URI);
        assertThat(targetUrl).contains("status=ALREADY_LINKED");
    }

    @Test
    @DisplayName("Should redirect with LINK_ERROR when Google account is already linked to another user")
    void onAuthenticationSuccess_LinkFlow_AlreadyLinkedToAnotherUser() throws Exception {
        // Arrange
        String googleSub = "google_link_other_user";
        String state = "oauth2_state_other";
        Long currentUserId = 10L;
        Long otherUserId = 20L;
        User otherUser = new User();
        otherUser.setId(otherUserId);
        otherUser.setUsername("other_user");
        AuthAccount existingAccount = AuthAccount.builder()
                .id(1L)
                .user(otherUser)
                .provider(AuthProvider.GOOGLE)
                .providerUserId(googleSub)
                .build();

        Map<String, Object> attributes = Map.of(
                "sub", googleSub,
                "email", "other@company.com"
        );
        OAuth2User oauth2User = new DefaultOAuth2User(
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "sub"
        );
        OAuth2AuthenticationToken authToken = new OAuth2AuthenticationToken(
                oauth2User,
                oauth2User.getAuthorities(),
                "google"
        );

        when(response.isCommitted()).thenReturn(false);
        when(request.getParameter("state")).thenReturn(state);
        when(oauth2LinkStateStore.getAndRemovePendingOAuth2State(state)).thenReturn(currentUserId);
        when(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, googleSub))
                .thenReturn(Optional.of(existingAccount));

        // Act
        successHandler.onAuthenticationSuccess(request, response, authToken);

        // Assert
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(redirectStrategy).sendRedirect(any(), any(), urlCaptor.capture());

        String targetUrl = urlCaptor.getValue();
        assertThat(targetUrl).startsWith(REDIRECT_URI);
        assertThat(targetUrl).contains("status=LINK_ERROR");
        assertThat(targetUrl).contains("error=account_already_linked_to_another_user");
    }

    @Test
    @DisplayName("Should redirect with LINK_ERROR when current user is already linked to a Google account")
    void onAuthenticationSuccess_LinkFlow_CurrentUserAlreadyHasGoogleAccount() throws Exception {
        // Arrange
        String googleSub = "google_link_new_sub";
        String state = "oauth2_state_user_linked";
        Long currentUserId = 10L;
        User currentUser = new User();
        currentUser.setId(currentUserId);
        currentUser.setUsername("buyer");
        AuthAccount existingAccountForUser = AuthAccount.builder()
                .id(2L)
                .user(currentUser)
                .provider(AuthProvider.GOOGLE)
                .providerUserId("other_sub")
                .build();

        Map<String, Object> attributes = Map.of(
                "sub", googleSub,
                "email", "buyer_new@company.com"
        );
        OAuth2User oauth2User = new DefaultOAuth2User(
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "sub"
        );
        OAuth2AuthenticationToken authToken = new OAuth2AuthenticationToken(
                oauth2User,
                oauth2User.getAuthorities(),
                "google"
        );

        when(response.isCommitted()).thenReturn(false);
        when(request.getParameter("state")).thenReturn(state);
        when(oauth2LinkStateStore.getAndRemovePendingOAuth2State(state)).thenReturn(currentUserId);
        when(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, googleSub))
                .thenReturn(Optional.empty());
        when(authAccountRepository.findByUserIdAndProvider(currentUserId, AuthProvider.GOOGLE))
                .thenReturn(Optional.of(existingAccountForUser));

        // Act
        successHandler.onAuthenticationSuccess(request, response, authToken);

        // Assert
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(redirectStrategy).sendRedirect(any(), any(), urlCaptor.capture());

        String targetUrl = urlCaptor.getValue();
        assertThat(targetUrl).startsWith(REDIRECT_URI);
        assertThat(targetUrl).contains("status=LINK_ERROR");
        assertThat(targetUrl).contains("error=user_already_linked_to_different_account");
    }

}
