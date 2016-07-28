/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.qualitygate.ws;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.utils.System2;
import org.sonar.api.web.UserRole;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.qualitygate.QualityGateDto;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.WsActionTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.core.permission.GlobalPermissions.QUALITY_GATE_ADMIN;
import static org.sonar.core.permission.GlobalPermissions.QUALITY_PROFILE_ADMIN;
import static org.sonar.db.component.ComponentTesting.newProjectDto;
import static org.sonar.server.qualitygate.QualityGates.SONAR_QUALITYGATE_PROPERTY;

public class SelectActionTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();

  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);
  DbClient dbClient = db.getDbClient();
  DbSession dbSession = db.getSession();
  ComponentDbTester componentDb = new ComponentDbTester(db);

  WsActionTester ws;

  SelectAction underTest;

  @Before
  public void setUp() {
    ComponentFinder componentFinder = new ComponentFinder(dbClient);
    underTest = new SelectAction(dbClient, userSession, componentFinder);
    ws = new WsActionTester(underTest);

    userSession.login("login").setGlobalPermissions(QUALITY_GATE_ADMIN);
  }

  @Test
  public void select_by_id() throws Exception {
    ComponentDto project = insertProject();
    QualityGateDto gate = insertQualityGate();
    String gateId = String.valueOf(gate.getId());

    callById(gateId, project.getId());
    assertSelected(gateId, project.getId());
  }

  @Test
  public void select_by_key() throws Exception {
    ComponentDto project = insertProject();
    QualityGateDto gate = insertQualityGate();
    String gateId = String.valueOf(gate.getId());

    callByKey(gateId, project.getKey());
    assertSelected(gateId, project.getId());
  }

  @Test
  public void project_admin() throws Exception {
    ComponentDto project = insertProject();
    QualityGateDto gate = insertQualityGate();
    String gateId = String.valueOf(gate.getId());

    userSession.login("login").addProjectUuidPermissions(UserRole.ADMIN, project.uuid());

    callByKey(gateId, project.getKey());
    assertSelected(gateId, project.getId());
  }

  @Test
  public void fail_when_no_quality_gate() throws Exception {
    ComponentDto project = insertProject();

    expectedException.expect(NotFoundException.class);
    callByKey("1", project.getKey());
  }

  @Test
  public void fail_when_no_project_id() throws Exception {
    QualityGateDto gate = insertQualityGate();
    String gateId = String.valueOf(gate.getId());

    expectedException.expect(NotFoundException.class);
    callById(gateId, 1L);
  }

  @Test
  public void fail_when_no_project_key() throws Exception {
    QualityGateDto gate = insertQualityGate();
    String gateId = String.valueOf(gate.getId());

    expectedException.expect(NotFoundException.class);
    callByKey(gateId, "unknown");
  }

  @Test
  public void fail_when_anonymous() throws Exception {
    ComponentDto project = insertProject();
    QualityGateDto gate = insertQualityGate();
    String gateId = String.valueOf(gate.getId());

    userSession.anonymous();

    expectedException.expect(ForbiddenException.class);
    callByKey(gateId, project.getKey());
  }

  @Test
  public void fail_when_not_project_admin() throws Exception {
    ComponentDto project = insertProject();
    QualityGateDto gate = insertQualityGate();
    String gateId = String.valueOf(gate.getId());

    userSession.login("login").addProjectUuidPermissions(UserRole.ISSUE_ADMIN, project.uuid());

    expectedException.expect(ForbiddenException.class);
    callByKey(gateId, project.getKey());
  }

  @Test
  public void fail_when_not_quality_gates_admin() throws Exception {
    ComponentDto project = insertProject();
    QualityGateDto gate = insertQualityGate();
    String gateId = String.valueOf(gate.getId());

    userSession.login("login").setGlobalPermissions(QUALITY_PROFILE_ADMIN);

    expectedException.expect(ForbiddenException.class);
    callByKey(gateId, project.getKey());
  }

  private ComponentDto insertProject() {
    return componentDb.insertComponent(newProjectDto());
  }

  private QualityGateDto insertQualityGate() {
    QualityGateDto gate = new QualityGateDto().setName("Custom");
    dbClient.qualityGateDao().insert(gate, dbSession);
    dbSession.commit();
    return gate;
  }

  private void callByKey(String gateId, String projectKey) {
    ws.newRequest()
      .setMethod("POST")
      .setParam("gateId", String.valueOf(gateId))
      .setParam("projectKey", projectKey)
      .execute();
  }

  private void callById(String gateId, Long projectId) {
    ws.newRequest()
      .setMethod("POST")
      .setParam("gateId", String.valueOf(gateId))
      .setParam("projectId", String.valueOf(projectId))
      .execute();
  }

  private void assertSelected(String gateId, Long projectId) {
    assertThat(dbClient.propertiesDao().selectProjectProperty(projectId, SONAR_QUALITYGATE_PROPERTY).getValue()).isEqualTo(gateId);
  }
}
