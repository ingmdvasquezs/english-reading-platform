package com.soap.soap;

import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Component
public class EditorialTestFixtureHelper {

  public static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  public static final String TEST_USER_EMAIL = "integration-test@example.invalid";
  public static final String TEST_COLLECTION_KEY = "colombian-myths-legends";

  @Autowired private DataSource dataSource;

  public void seedCatalogIfNeeded() {
    var populator =
        new ResourceDatabasePopulator(new ClassPathResource("sql/editorial-test-fixtures.sql"));
    populator.execute(dataSource);
  }
}
