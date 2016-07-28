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

import org.elasticsearch.common.Nullable;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.web.UserRole;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.property.PropertyDto;
import org.sonar.db.qualitygate.QualityGateDto;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.component.ComponentFinder.ParamNames;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.user.UserSession;
import org.sonarqube.ws.client.qualitygate.SelectWsRequest;

import static org.sonar.server.qualitygate.QualityGates.SONAR_QUALITYGATE_PROPERTY;
import static org.sonar.server.ws.KeyExamples.KEY_PROJECT_EXAMPLE_001;
import static org.sonarqube.ws.client.qualitygate.QualityGatesWsParameters.PARAM_GATE_ID;
import static org.sonarqube.ws.client.qualitygate.QualityGatesWsParameters.PARAM_PROJECT_ID;
import static org.sonarqube.ws.client.qualitygate.QualityGatesWsParameters.PARAM_PROJECT_KEY;

public class SelectAction implements QGateWsAction {
  private final DbClient dbClient;
  private final UserSession userSession;
  private final ComponentFinder componentFinder;

  public SelectAction(DbClient dbClient, UserSession userSession, ComponentFinder componentFinder) {
    this.dbClient = dbClient;
    this.userSession = userSession;
    this.componentFinder = componentFinder;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("select")
      .setDescription("Associate a project to a quality gate. Require Administer Quality Gates permission.")
      .setPost(true)
      .setSince("4.3")
      .setHandler(this);

    action.createParam(PARAM_GATE_ID)
      .setDescription("Quality gate id")
      .setRequired(true)
      .setExampleValue("1");

    action.createParam(PARAM_PROJECT_ID)
      .setDescription("Project id")
      .setExampleValue("12")
      .setDeprecatedSince("6.1");

    action.createParam(PARAM_PROJECT_KEY)
      .setDescription("Project key")
      .setExampleValue(KEY_PROJECT_EXAMPLE_001)
      .setSince("6.1");
  }

  @Override
  public void handle(Request request, Response response) {
    doHandle(toSelectWsRequest(request));
    response.noContent();
  }

  private void doHandle(SelectWsRequest request) {
    DbSession dbSession = dbClient.openSession(false);
    try {
      checkQualityGate(dbClient, request.getGateId());
      ComponentDto project = getProject(dbSession, request.getProjectId(), request.getProjectKey());

      dbClient.propertiesDao().insertProperty(dbSession, new PropertyDto()
        .setKey(SONAR_QUALITYGATE_PROPERTY)
        .setResourceId(project.getId())
        .setValue(String.valueOf(request.getGateId())));

      dbSession.commit();
    } finally {
      dbClient.closeSession(dbSession);
    }
  }

  private static SelectWsRequest toSelectWsRequest(Request request) {
    return new SelectWsRequest()
      .setGateId(request.mandatoryParamAsLong(PARAM_GATE_ID))
      .setProjectId(request.paramAsLong(PARAM_PROJECT_ID))
      .setProjectKey(request.param(PARAM_PROJECT_KEY));
  }

  private ComponentDto getProject(DbSession dbSession, @Nullable Long projectId, @Nullable String projectKey) {
    ComponentDto project = componentFinder.getByIdOrKey(dbSession, projectId, projectKey, ParamNames.PROJECT_ID_AND_KEY);

    if (!userSession.hasPermission(GlobalPermissions.QUALITY_GATE_ADMIN) &&
      !userSession.hasComponentUuidPermission(UserRole.ADMIN, project.uuid())) {
      throw new ForbiddenException("Insufficient privileges");
    }

    return project;
  }

  private static void checkQualityGate(DbClient dbClient, long id) {
    QualityGateDto gate = dbClient.qualityGateDao().selectById(id);
    if (gate == null) {
      throw new NotFoundException("There is no quality gate with id=" + id);
    }
  }
}
