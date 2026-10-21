package com.shuati.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuati.common.ApiException;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class RobotsPolicyTest {

  private static final String ROBOTS_URL = "https://example.com/robots.txt";
  private static final String PAGE_URL = "https://example.com/page";

  @Test
  void missingRobotsAllowsEverything() {
    assertThat(RobotsPolicy.isAllowedByRobots("", ROBOTS_URL, PAGE_URL)).isTrue();
    assertThat(RobotsPolicy.isAllowedByRobots(null, ROBOTS_URL, PAGE_URL)).isTrue();
  }

  @Test
  void disallowAllBlocks() {
    assertThat(RobotsPolicy.isAllowedByRobots(
        "User-agent: *\nDisallow: /", ROBOTS_URL, PAGE_URL)).isFalse();
  }

  @Test
  void pathSpecificRulesAreRespected() {
    String robots = "User-agent: *\nDisallow: /private";
    assertThat(RobotsPolicy.isAllowedByRobots(
        robots, ROBOTS_URL, "https://example.com/private/x")).isFalse();
    assertThat(RobotsPolicy.isAllowedByRobots(
        robots, ROBOTS_URL, "https://example.com/public")).isTrue();
  }

  @Test
  void ourOwnUserAgentRuleWins() {
    String robots = """
        User-agent: *
        Disallow: /

        User-agent: ShuatiBot
        Allow: /
        """;
    assertThat(RobotsPolicy.isAllowedByRobots(robots, ROBOTS_URL, PAGE_URL)).isTrue();
  }

  @Test
  void blocksLoopbackPrivateAndMetadataAddresses() throws Exception {
    for (String ip : new String[]{
        "127.0.0.1", "10.0.0.1", "192.168.1.1", "169.254.169.254", "100.64.0.1", "0.0.0.0"}) {
      assertThat(RobotsPolicy.isPrivateAddress(InetAddress.getByName(ip)))
          .as(ip).isTrue();
    }
    assertThat(RobotsPolicy.isPrivateAddress(InetAddress.getByName("8.8.8.8"))).isFalse();
  }

  @Test
  void rejectsNonHttpAndLocalhostBeforeAnyNetworkCall() {
    RobotsPolicy policy = new RobotsPolicy();
    assertThatThrownBy(() -> policy.assertCrawlAllowed("ftp://example.com/a"))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> policy.assertCrawlAllowed("http://127.0.0.1:8080/a"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("内网");
  }
}
