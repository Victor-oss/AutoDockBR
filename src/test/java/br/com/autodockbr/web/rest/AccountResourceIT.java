package br.com.autodockbr.web.rest;

import static br.com.autodockbr.web.rest.AccountResourceIT.TEST_USER_LOGIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.autodockbr.IntegrationTest;
import br.com.autodockbr.config.Constants;
import br.com.autodockbr.domain.User;
import br.com.autodockbr.repository.UserRepository;
import br.com.autodockbr.service.UserService;
import br.com.autodockbr.service.dto.AdminUserDTO;
import br.com.autodockbr.service.dto.PasswordChangeDTO;
import br.com.autodockbr.web.rest.vm.KeyAndPasswordVM;
import br.com.autodockbr.web.rest.vm.ManagedUserVM;
import java.time.Instant;
import java.util.*;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the {@link AccountResource} REST controller.
 */
@AutoConfigureMockMvc
@WithMockUser(value = TEST_USER_LOGIN)
@IntegrationTest
class AccountResourceIT {

    static final String TEST_USER_LOGIN = "test";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc restAccountMockMvc;

    @Test
    @WithUnauthenticatedMockUser
    void testNonAuthenticatedUser() throws Exception {
        restAccountMockMvc
            .perform(get("/api/authenticate").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().string(""));
    }

    @Test
    void testAuthenticatedUser() throws Exception {
        restAccountMockMvc
            .perform(
                get("/api/authenticate")
                    .with(request -> {
                        request.setRemoteUser(TEST_USER_LOGIN);
                        return request;
                    })
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(content().string(TEST_USER_LOGIN));
    }

    @Test
    void testGetExistingAccount() throws Exception {
        AdminUserDTO user = new AdminUserDTO();
        user.setName("john");
        user.setEmail("john.doe@jhipster.com");
        user.setLangKey("en");
        userService.createUser(user);

        restAccountMockMvc
            .perform(get("/api/account").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.login").value(TEST_USER_LOGIN))
            .andExpect(jsonPath("$.firstName").value("john"))
            .andExpect(jsonPath("$.lastName").value("doe"))
            .andExpect(jsonPath("$.email").value("john.doe@jhipster.com"))
            .andExpect(jsonPath("$.imageUrl").value("http://placehold.it/50x50"))
            .andExpect(jsonPath("$.langKey").value("en"));
    }

    @Test
    void testGetUnknownAccount() throws Exception {
        restAccountMockMvc
            .perform(get("/api/account").accept(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(status().isInternalServerError());
    }

    @Test
    @Transactional
    void testRegisterValid() throws Exception {
        ManagedUserVM validUser = new ManagedUserVM();
        validUser.setPassword("password");
        validUser.setName("Alice");
        validUser.setEmail("test-register-valid@example.com");
        validUser.setLangKey(Constants.DEFAULT_LANGUAGE);
        assertThat(userRepository.findOneByEmailIgnoreCase("test-register-valid@email.com")).isEmpty();

        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(validUser)))
            .andExpect(status().isCreated());

        assertThat(userRepository.findOneByEmailIgnoreCase("test-register-valid@email.com")).isPresent();
    }

    @Test
    @Transactional
    void testRegisterInvalidLogin() throws Exception {
        ManagedUserVM invalidUser = new ManagedUserVM();
        invalidUser.setPassword("password");
        invalidUser.setName("Funky");
        invalidUser.setEmail("funky@example.com");
        invalidUser.setActivated(true);
        invalidUser.setLangKey(Constants.DEFAULT_LANGUAGE);

        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(invalidUser)))
            .andExpect(status().isBadRequest());

        Optional<User> user = userRepository.findOneByEmailIgnoreCase("funky@example.com");
        assertThat(user).isEmpty();
    }

    @Test
    @Transactional
    void testRegisterInvalidEmail() throws Exception {
        ManagedUserVM invalidUser = new ManagedUserVM();
        invalidUser.setPassword("password");
        invalidUser.setName("Bob");
        invalidUser.setEmail("invalid"); // <-- invalid
        invalidUser.setActivated(true);
        invalidUser.setLangKey(Constants.DEFAULT_LANGUAGE);

        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(invalidUser)))
            .andExpect(status().isBadRequest());

        Optional<User> user = userRepository.findOneByEmailIgnoreCase("bob");
        assertThat(user).isEmpty();
    }

    @Test
    @Transactional
    void testRegisterInvalidPassword() throws Exception {
        ManagedUserVM invalidUser = new ManagedUserVM();
        invalidUser.setPassword("123"); // password with only 3 digits
        invalidUser.setName("Bob");
        invalidUser.setEmail("bob@example.com");
        invalidUser.setActivated(true);
        invalidUser.setLangKey(Constants.DEFAULT_LANGUAGE);

        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(invalidUser)))
            .andExpect(status().isBadRequest());

        Optional<User> user = userRepository.findOneByEmailIgnoreCase("bob");
        assertThat(user).isEmpty();
    }

    @Test
    @Transactional
    void testRegisterNullPassword() throws Exception {
        ManagedUserVM invalidUser = new ManagedUserVM();
        invalidUser.setPassword(null); // invalid null password
        invalidUser.setName("Bob");
        invalidUser.setEmail("bob@example.com");
        invalidUser.setActivated(true);
        invalidUser.setLangKey(Constants.DEFAULT_LANGUAGE);

        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(invalidUser)))
            .andExpect(status().isBadRequest());

        Optional<User> user = userRepository.findOneByEmailIgnoreCase("bob");
        assertThat(user).isEmpty();
    }

    @Test
    @Transactional
    void testRegisterDuplicateLogin() throws Exception {
        // First registration
        ManagedUserVM firstUser = new ManagedUserVM();
        firstUser.setPassword("password");
        firstUser.setName("Alice");
        firstUser.setEmail("alice@example.com");
        firstUser.setLangKey(Constants.DEFAULT_LANGUAGE);

        // Duplicate login, different email
        ManagedUserVM secondUser = new ManagedUserVM();
        secondUser.setPassword(firstUser.getPassword());
        secondUser.setName(firstUser.getName());
        secondUser.setEmail("alice2@example.com");
        secondUser.setLangKey(firstUser.getLangKey());
        secondUser.setCreatedBy(firstUser.getCreatedBy());
        secondUser.setCreatedDate(firstUser.getCreatedDate());
        secondUser.setLastModifiedBy(firstUser.getLastModifiedBy());
        secondUser.setLastModifiedDate(firstUser.getLastModifiedDate());

        // First user
        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(firstUser)))
            .andExpect(status().isCreated());

        // Second (non activated) user
        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(secondUser)))
            .andExpect(status().isCreated());

        Optional<User> testUser = userRepository.findOneByEmailIgnoreCase("alice2@example.com");
        assertThat(testUser).isPresent();
        testUser.get().setActivated(true);
        userRepository.save(testUser.get());

        // Second (already activated) user
        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(secondUser)))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @Transactional
    void testRegisterDuplicateEmail() throws Exception {
        // First user
        ManagedUserVM firstUser = new ManagedUserVM();
        firstUser.setPassword("password");
        firstUser.setName("Alice");
        firstUser.setEmail("test-register-duplicate-email@example.com");
        firstUser.setLangKey(Constants.DEFAULT_LANGUAGE);

        // Register first user
        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(firstUser)))
            .andExpect(status().isCreated());

        Optional<User> testUser1 = userRepository.findOneByEmailIgnoreCase("test-register-duplicate-email");
        assertThat(testUser1).isPresent();

        // Duplicate email, different login
        ManagedUserVM secondUser = new ManagedUserVM();
        secondUser.setPassword(firstUser.getPassword());
        secondUser.setName(firstUser.getName());
        secondUser.setEmail(firstUser.getEmail());
        secondUser.setLangKey(firstUser.getLangKey());

        // Register second (non activated) user
        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(secondUser)))
            .andExpect(status().isCreated());

        Optional<User> testUser2 = userRepository.findOneByEmailIgnoreCase("test-register-duplicate-email");
        assertThat(testUser2).isEmpty();

        Optional<User> testUser3 = userRepository.findOneByEmailIgnoreCase("test-register-duplicate-email-2");
        assertThat(testUser3).isPresent();

        // Duplicate email - with uppercase email address
        ManagedUserVM userWithUpperCaseEmail = new ManagedUserVM();
        userWithUpperCaseEmail.setId(firstUser.getId());
        userWithUpperCaseEmail.setPassword(firstUser.getPassword());
        userWithUpperCaseEmail.setName(firstUser.getName());
        userWithUpperCaseEmail.setEmail("TEST-register-duplicate-email@example.com");
        userWithUpperCaseEmail.setLangKey(firstUser.getLangKey());

        // Register third (not activated) user
        restAccountMockMvc
            .perform(
                post("/api/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(userWithUpperCaseEmail))
            )
            .andExpect(status().isCreated());

        Optional<User> testUser4 = userRepository.findOneByEmailIgnoreCase("test-register-duplicate-email-3");
        assertThat(testUser4).isPresent();
        assertThat(testUser4.get().getEmail()).isEqualTo("test-register-duplicate-email@example.com");

        testUser4.get().setActivated(true);
        userService.updateUser((new AdminUserDTO(testUser4.get())));

        // Register 4th (already activated) user
        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(secondUser)))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @Transactional
    void testActivateAccount() throws Exception {
        final String activationKey = "some activation key";
        User user = new User();
        user.setEmail("activate-account@example.com");
        user.setPassword(RandomStringUtils.randomAlphanumeric(60));
        user.setActivated(false);
        user.setActivationKey(activationKey);

        userRepository.saveAndFlush(user);

        restAccountMockMvc.perform(get("/api/activate?key={activationKey}", activationKey)).andExpect(status().isOk());

        user = userRepository.findOneByEmailIgnoreCase(user.getEmail()).orElse(null);
        assertThat(user.isActivated()).isTrue();
    }

    @Test
    @Transactional
    void testActivateAccountWithWrongKey() throws Exception {
        restAccountMockMvc.perform(get("/api/activate?key=wrongActivationKey")).andExpect(status().isInternalServerError());
    }

    @Test
    @Transactional
    @WithMockUser("save-account")
    void testSaveAccount() throws Exception {
        User user = new User();
        user.setEmail("save-account@example.com");
        user.setPassword(RandomStringUtils.randomAlphanumeric(60));
        user.setActivated(true);
        userRepository.saveAndFlush(user);

        AdminUserDTO userDTO = new AdminUserDTO();
        userDTO.setName("firstname");
        userDTO.setEmail("save-account@example.com");
        userDTO.setActivated(false);
        userDTO.setLangKey(Constants.DEFAULT_LANGUAGE);

        restAccountMockMvc
            .perform(post("/api/account").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(userDTO)))
            .andExpect(status().isOk());

        User updatedUser = userRepository.findOneByEmailIgnoreCase(user.getEmail()).orElse(null);
        assertThat(updatedUser.getName()).isEqualTo(userDTO.getName());
        assertThat(updatedUser.getEmail()).isEqualTo(userDTO.getEmail());
        assertThat(updatedUser.getPassword()).isEqualTo(user.getPassword());
        assertThat(updatedUser.isActivated()).isTrue();
    }

    @Test
    @Transactional
    @WithMockUser("save-invalid-email")
    void testSaveInvalidEmail() throws Exception {
        User user = new User();
        user.setEmail("save-invalid-email@example.com");
        user.setPassword(RandomStringUtils.randomAlphanumeric(60));
        user.setActivated(true);

        userRepository.saveAndFlush(user);

        AdminUserDTO userDTO = new AdminUserDTO();
        userDTO.setName("firstname");
        userDTO.setEmail("invalid email");
        userDTO.setActivated(false);
        userDTO.setLangKey(Constants.DEFAULT_LANGUAGE);

        restAccountMockMvc
            .perform(post("/api/account").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(userDTO)))
            .andExpect(status().isBadRequest());

        assertThat(userRepository.findOneByEmailIgnoreCase("invalid email")).isNotPresent();
    }

    @Test
    @Transactional
    @WithMockUser("save-existing-email")
    void testSaveExistingEmail() throws Exception {
        User user = new User();
        user.setEmail("save-existing-email@example.com");
        user.setPassword(RandomStringUtils.randomAlphanumeric(60));
        user.setActivated(true);
        userRepository.saveAndFlush(user);

        User anotherUser = new User();
        anotherUser.setEmail("save-existing-email2@example.com");
        anotherUser.setPassword(RandomStringUtils.randomAlphanumeric(60));
        anotherUser.setActivated(true);

        userRepository.saveAndFlush(anotherUser);

        AdminUserDTO userDTO = new AdminUserDTO();
        userDTO.setName("firstname");
        userDTO.setEmail("save-existing-email2@example.com");
        userDTO.setActivated(false);
        userDTO.setLangKey(Constants.DEFAULT_LANGUAGE);

        restAccountMockMvc
            .perform(post("/api/account").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(userDTO)))
            .andExpect(status().isBadRequest());

        User updatedUser = userRepository.findOneByEmailIgnoreCase("save-existing-email").orElse(null);
        assertThat(updatedUser.getEmail()).isEqualTo("save-existing-email@example.com");
    }

    @Test
    @Transactional
    @WithMockUser("save-existing-email-and-login")
    void testSaveExistingEmailAndLogin() throws Exception {
        User user = new User();
        user.setEmail("save-existing-email-and-login@example.com");
        user.setPassword(RandomStringUtils.randomAlphanumeric(60));
        user.setActivated(true);
        userRepository.saveAndFlush(user);

        AdminUserDTO userDTO = new AdminUserDTO();
        userDTO.setName("firstname");
        userDTO.setEmail("save-existing-email-and-login@example.com");
        userDTO.setActivated(false);
        userDTO.setLangKey(Constants.DEFAULT_LANGUAGE);

        restAccountMockMvc
            .perform(post("/api/account").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(userDTO)))
            .andExpect(status().isOk());

        User updatedUser = userRepository.findOneByEmailIgnoreCase("save-existing-email-and-login").orElse(null);
        assertThat(updatedUser.getEmail()).isEqualTo("save-existing-email-and-login@example.com");
    }

    @Test
    @Transactional
    @WithMockUser("change-password-wrong-existing-password")
    void testChangePasswordWrongExistingPassword() throws Exception {
        User user = new User();
        String currentPassword = RandomStringUtils.randomAlphanumeric(60);
        user.setPassword(passwordEncoder.encode(currentPassword));
        user.setEmail("change-password-wrong-existing-password@example.com");
        userRepository.saveAndFlush(user);

        restAccountMockMvc
            .perform(
                post("/api/account/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(new PasswordChangeDTO("1" + currentPassword, "new password")))
            )
            .andExpect(status().isBadRequest());

        User updatedUser = userRepository.findOneByEmailIgnoreCase("change-password-wrong-existing-password").orElse(null);
        assertThat(passwordEncoder.matches("new password", updatedUser.getPassword())).isFalse();
        assertThat(passwordEncoder.matches(currentPassword, updatedUser.getPassword())).isTrue();
    }

    @Test
    @Transactional
    @WithMockUser("change-password")
    void testChangePassword() throws Exception {
        User user = new User();
        String currentPassword = RandomStringUtils.randomAlphanumeric(60);
        user.setPassword(passwordEncoder.encode(currentPassword));
        user.setEmail("change-password@example.com");
        userRepository.saveAndFlush(user);

        restAccountMockMvc
            .perform(
                post("/api/account/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(new PasswordChangeDTO(currentPassword, "new password")))
            )
            .andExpect(status().isOk());

        User updatedUser = userRepository.findOneByEmailIgnoreCase("change-password").orElse(null);
        assertThat(passwordEncoder.matches("new password", updatedUser.getPassword())).isTrue();
    }

    @Test
    @Transactional
    @WithMockUser("change-password-too-small")
    void testChangePasswordTooSmall() throws Exception {
        User user = new User();
        String currentPassword = RandomStringUtils.randomAlphanumeric(60);
        user.setPassword(passwordEncoder.encode(currentPassword));
        user.setEmail("change-password-too-small@example.com");
        userRepository.saveAndFlush(user);

        String newPassword = RandomStringUtils.random(ManagedUserVM.PASSWORD_MIN_LENGTH - 1);

        restAccountMockMvc
            .perform(
                post("/api/account/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(new PasswordChangeDTO(currentPassword, newPassword)))
            )
            .andExpect(status().isBadRequest());

        User updatedUser = userRepository.findOneByEmailIgnoreCase("change-password-too-small").orElse(null);
        assertThat(updatedUser.getPassword()).isEqualTo(user.getPassword());
    }

    @Test
    @Transactional
    @WithMockUser("change-password-too-long")
    void testChangePasswordTooLong() throws Exception {
        User user = new User();
        String currentPassword = RandomStringUtils.randomAlphanumeric(60);
        user.setPassword(passwordEncoder.encode(currentPassword));
        user.setEmail("change-password-too-long");
        user.setEmail("change-password-too-long@example.com");
        userRepository.saveAndFlush(user);

        String newPassword = RandomStringUtils.random(ManagedUserVM.PASSWORD_MAX_LENGTH + 1);

        restAccountMockMvc
            .perform(
                post("/api/account/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(new PasswordChangeDTO(currentPassword, newPassword)))
            )
            .andExpect(status().isBadRequest());

        User updatedUser = userRepository.findOneByEmailIgnoreCase("change-password-too-long").orElse(null);
        assertThat(updatedUser.getPassword()).isEqualTo(user.getPassword());
    }

    @Test
    @Transactional
    @WithMockUser("change-password-empty")
    void testChangePasswordEmpty() throws Exception {
        User user = new User();
        String currentPassword = RandomStringUtils.randomAlphanumeric(60);
        user.setPassword(passwordEncoder.encode(currentPassword));
        user.setEmail("change-password-empty@example.com");
        userRepository.saveAndFlush(user);

        restAccountMockMvc
            .perform(
                post("/api/account/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(new PasswordChangeDTO(currentPassword, "")))
            )
            .andExpect(status().isBadRequest());

        User updatedUser = userRepository.findOneByEmailIgnoreCase("change-password-empty").orElse(null);
        assertThat(updatedUser.getPassword()).isEqualTo(user.getPassword());
    }

    @Test
    @Transactional
    void testRequestPasswordReset() throws Exception {
        User user = new User();
        user.setPassword(RandomStringUtils.randomAlphanumeric(60));
        user.setActivated(true);
        user.setEmail("password-reset@example.com");
        userRepository.saveAndFlush(user);

        restAccountMockMvc
            .perform(post("/api/account/reset-password/init").content("password-reset@example.com"))
            .andExpect(status().isOk());
    }

    @Test
    @Transactional
    void testRequestPasswordResetUpperCaseEmail() throws Exception {
        User user = new User();
        user.setPassword(RandomStringUtils.randomAlphanumeric(60));
        user.setActivated(true);
        user.setEmail("password-reset-upper-case@example.com");
        userRepository.saveAndFlush(user);

        restAccountMockMvc
            .perform(post("/api/account/reset-password/init").content("password-reset-upper-case@EXAMPLE.COM"))
            .andExpect(status().isOk());
    }

    @Test
    void testRequestPasswordResetWrongEmail() throws Exception {
        restAccountMockMvc
            .perform(post("/api/account/reset-password/init").content("password-reset-wrong-email@example.com"))
            .andExpect(status().isOk());
    }

    @Test
    @Transactional
    void testFinishPasswordReset() throws Exception {
        User user = new User();
        user.setPassword(RandomStringUtils.randomAlphanumeric(60));
        user.setEmail("finish-password-reset@example.com");
        user.setResetDate(Instant.now().plusSeconds(60));
        user.setResetKey("reset key");
        userRepository.saveAndFlush(user);

        KeyAndPasswordVM keyAndPassword = new KeyAndPasswordVM();
        keyAndPassword.setKey(user.getResetKey());
        keyAndPassword.setNewPassword("new password");

        restAccountMockMvc
            .perform(
                post("/api/account/reset-password/finish")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(keyAndPassword))
            )
            .andExpect(status().isOk());

        User updatedUser = userRepository.findOneByEmailIgnoreCase(user.getEmail()).orElse(null);
        assertThat(passwordEncoder.matches(keyAndPassword.getNewPassword(), updatedUser.getPassword())).isTrue();
    }

    @Test
    @Transactional
    void testFinishPasswordResetTooSmall() throws Exception {
        User user = new User();
        user.setPassword(RandomStringUtils.randomAlphanumeric(60));
        user.setEmail("finish-password-reset-too-small@example.com");
        user.setResetDate(Instant.now().plusSeconds(60));
        user.setResetKey("reset key too small");
        userRepository.saveAndFlush(user);

        KeyAndPasswordVM keyAndPassword = new KeyAndPasswordVM();
        keyAndPassword.setKey(user.getResetKey());
        keyAndPassword.setNewPassword("foo");

        restAccountMockMvc
            .perform(
                post("/api/account/reset-password/finish")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(keyAndPassword))
            )
            .andExpect(status().isBadRequest());

        User updatedUser = userRepository.findOneByEmailIgnoreCase(user.getEmail()).orElse(null);
        assertThat(passwordEncoder.matches(keyAndPassword.getNewPassword(), updatedUser.getPassword())).isFalse();
    }

    @Test
    @Transactional
    void testFinishPasswordResetWrongKey() throws Exception {
        KeyAndPasswordVM keyAndPassword = new KeyAndPasswordVM();
        keyAndPassword.setKey("wrong reset key");
        keyAndPassword.setNewPassword("new password");

        restAccountMockMvc
            .perform(
                post("/api/account/reset-password/finish")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(keyAndPassword))
            )
            .andExpect(status().isInternalServerError());
    }
}
