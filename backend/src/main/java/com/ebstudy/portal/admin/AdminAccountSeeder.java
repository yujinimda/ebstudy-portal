package com.ebstudy.portal.admin;

import com.ebstudy.portal.auth.AuthProperties;
import com.ebstudy.portal.user.Role;
import com.ebstudy.portal.user.User;
import com.ebstudy.portal.user.UserRepository;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-022 · AC-37 — 관리자 계정은 <b>없을 때만</b> 만들고 <b>반복 실행해도 안전</b>해야 한다.
 *
 * <p>이 코드는 부팅마다 돈다. 깨지면 "재시작할 때마다 관리자 비밀번호가 초기화된다" 또는
 * "유니크 제약 위반으로 부팅이 실패한다"로 나타난다. 그래서
 * <b>이미 있으면 아무 일도 하지 않고</b>(비밀번호를 덮어쓰지 않는다),
 * 경합으로 제약 위반이 나도 <b>부팅을 멈추지 않는다</b>.
 *
 * <p>FR-023 — 초기 비밀번호는 환경변수에만 있다. 저장소에 남기지 않으며 로그에도 찍지 않는다.
 */
@Component
public class AdminAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties.Seed seed;

    public AdminAccountSeeder(UserRepository users, PasswordEncoder passwordEncoder,
            AuthProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.seed = properties.seed();
    }

    @Override
    public void run(ApplicationArguments args) {
        seedIfAbsent();
    }

    @Transactional
    public boolean seedIfAbsent() {
        if (seed.adminUsername() == null || seed.adminUsername().isBlank()
                || seed.adminPassword() == null || seed.adminPassword().isBlank()) {
            log.warn("ADMIN_INITIAL_ID / ADMIN_INITIAL_PASSWORD 가 없어 관리자 시딩을 건너뛴다. "
                    + "관리자 로그인이 필요하면 .env 에 값을 채우고 다시 시작한다.");
            return false;
        }
        if (users.existsByRole(Role.ADMIN)) {
            log.info("관리자 계정이 이미 있어 시딩을 건너뛴다(멱등).");
            return false;
        }
        try {
            users.saveAndFlush(User.create(seed.adminUsername(),
                    passwordEncoder.encode(seed.adminPassword()),
                    seed.adminName() == null || seed.adminName().isBlank() ? "관리자" : seed.adminName(),
                    Role.ADMIN, OffsetDateTime.now()));
            log.info("관리자 계정을 생성했다. username={}", seed.adminUsername());
            return true;
        } catch (DataIntegrityViolationException ex) {
            // 같은 아이디가 이미 있거나 동시에 시딩됐다 — 부팅을 실패시키지 않는다(AC-37)
            log.warn("관리자 시딩이 유니크 제약에 걸려 건너뛴다. 이미 같은 아이디가 존재한다.");
            return false;
        }
    }
}
