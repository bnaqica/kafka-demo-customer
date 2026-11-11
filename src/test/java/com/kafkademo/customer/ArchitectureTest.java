package com.kafkademo.customer;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

class ArchitectureTest {

    private static final String BASE = "com.kafkademo.customer";

    @Test
    void onlyCrudServiceImplementationsInjectRepositories() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));

        Set<String> injecting = new TreeSet<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(BASE)) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            if (type.getName().endsWith("Test") || type.getName().endsWith("Tests")) {
                continue;
            }
            boolean usesRepository = Arrays.stream(type.getDeclaredFields())
                    .map(Field::getType)
                    .anyMatch(fieldType -> fieldType.getPackageName().equals(BASE + ".repository"));
            if (usesRepository) {
                injecting.add(type.getSimpleName());
            }
        }

        assertThat(injecting)
                .containsExactly(
                        "ApiAuditServiceImpl",
                        "CustomerPhoneServiceImpl",
                        "CustomerServiceImpl",
                        "IdempotencyKeyServiceImpl");
    }
}
