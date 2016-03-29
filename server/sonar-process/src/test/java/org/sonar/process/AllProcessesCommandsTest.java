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
package org.sonar.process;

import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.sonar.process.ProcessCommands.MAX_PROCESSES;

public class AllProcessesCommandsTest {

  private static final int PROCESS_NUMBER = 1;
  private static final byte STOP = (byte) 0xFF;
  private static final byte RESTART = (byte) 0xAA;
  private static final byte UP = (byte) 0x01;
  private static final byte OPERATIONAL = (byte) 0x59;
  private static final byte EMPTY = (byte) 0x00;

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void fail_to_init_if_dir_does_not_exist() throws Exception {
    File dir = temp.newFolder();
    FileUtils.deleteQuietly(dir);

    try {
      new AllProcessesCommands(dir);
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessage("Not a valid directory: " + dir.getAbsolutePath());
    }
  }

  @Test
  public void write_and_read_up() throws IOException {
    AllProcessesCommands commands = new AllProcessesCommands(temp.newFolder());
    int offset = 0;

    assertThat(commands.isUp(PROCESS_NUMBER)).isFalse();
    assertThat(readByte(commands, offset)).isEqualTo(EMPTY);

    commands.setUp(PROCESS_NUMBER);
    assertThat(commands.isUp(PROCESS_NUMBER)).isTrue();
    assertThat(readByte(commands, offset)).isEqualTo(UP);
  }

  @Test
  public void write_and_read_operational() throws IOException {
    AllProcessesCommands commands = new AllProcessesCommands(temp.newFolder());
    int offset = 3;

    assertThat(commands.isOperational(PROCESS_NUMBER)).isFalse();
    assertThat(readByte(commands, offset)).isEqualTo(EMPTY);

    commands.setOperational(PROCESS_NUMBER);
    assertThat(commands.isOperational(PROCESS_NUMBER)).isTrue();
    assertThat(readByte(commands, offset)).isEqualTo(OPERATIONAL);
  }

  @Test
  public void write_and_read_ping() throws IOException {
    AllProcessesCommands commands = new AllProcessesCommands(temp.newFolder());

    int offset = 4;
    assertThat(readLong(commands, offset)).isEqualTo(0L);

    long currentTime = System.currentTimeMillis();
    commands.ping(PROCESS_NUMBER);
    assertThat(readLong(commands, offset)).isGreaterThanOrEqualTo(currentTime);
  }

  @Test
  public void write_and_read_jmx_url() throws IOException {
    AllProcessesCommands commands = new AllProcessesCommands(temp.newFolder());

    int offset = 12;
    for (int i = 0; i < 500; i++) {
      assertThat(readByte(commands, offset + i)).isEqualTo(EMPTY);
    }

    commands.setJmxUrl(PROCESS_NUMBER, "jmx:foo");
    assertThat(readByte(commands, offset)).isNotEqualTo(EMPTY);
    assertThat(commands.getJmxUrl(PROCESS_NUMBER)).isEqualTo("jmx:foo");
  }

  @Test
  public void ask_for_stop() throws Exception {
    AllProcessesCommands commands = new AllProcessesCommands(temp.newFolder());
    int offset = 1;

    assertThat(readByte(commands, offset)).isNotEqualTo(STOP);
    assertThat(commands.askedForStop(PROCESS_NUMBER)).isFalse();

    commands.askForStop(PROCESS_NUMBER);
    assertThat(commands.askedForStop(PROCESS_NUMBER)).isTrue();
    assertThat(readByte(commands, offset)).isEqualTo(STOP);
  }

  @Test
  public void ask_for_restart() throws Exception {
    AllProcessesCommands commands = new AllProcessesCommands(temp.newFolder());
    int offset = 2;

    assertThat(readByte(commands, offset)).isNotEqualTo(RESTART);
    assertThat(commands.askedForRestart(PROCESS_NUMBER)).isFalse();

    commands.askForRestart(PROCESS_NUMBER);
    assertThat(commands.askedForRestart(PROCESS_NUMBER)).isTrue();
    assertThat(readByte(commands, offset)).isEqualTo(RESTART);
  }

  @Test
  public void acknowledgeAskForRestart_has_no_effect_when_no_restart_asked() throws Exception {
    AllProcessesCommands commands = new AllProcessesCommands(temp.newFolder());
    int offset = 2;

    assertThat(readByte(commands, offset)).isNotEqualTo(RESTART);
    assertThat(commands.askedForRestart(PROCESS_NUMBER)).isFalse();

    commands.acknowledgeAskForRestart(PROCESS_NUMBER);
    assertThat(readByte(commands, offset)).isNotEqualTo(RESTART);
    assertThat(commands.askedForRestart(PROCESS_NUMBER)).isFalse();
  }

  @Test
  public void acknowledgeAskForRestart_resets_askForRestart_has_no_effect_when_no_restart_asked() throws Exception {
    AllProcessesCommands commands = new AllProcessesCommands(temp.newFolder());
    int offset = 2;

    commands.askForRestart(PROCESS_NUMBER);
    assertThat(commands.askedForRestart(PROCESS_NUMBER)).isTrue();
    assertThat(readByte(commands, offset)).isEqualTo(RESTART);

    commands.acknowledgeAskForRestart(PROCESS_NUMBER);
    assertThat(readByte(commands, offset)).isNotEqualTo(RESTART);
    assertThat(commands.askedForRestart(PROCESS_NUMBER)).isFalse();
  }

  @Test
  public void getProcessCommands_fails_if_processNumber_is_less_than_0() throws Exception {
    AllProcessesCommands allProcessesCommands = new AllProcessesCommands(temp.newFolder());
    int processNumber = -2;

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Process number " + processNumber + " is not valid");

    allProcessesCommands.createAfterClean(processNumber);
  }

  @Test
  public void getProcessCommands_fails_if_processNumber_is_higher_than_MAX_PROCESSES() throws Exception {
    AllProcessesCommands allProcessesCommands = new AllProcessesCommands(temp.newFolder());
    int processNumber = MAX_PROCESSES + 1;

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Process number " + processNumber + " is not valid");

    allProcessesCommands.createAfterClean(processNumber);
  }

  private byte readByte(AllProcessesCommands commands, int offset) {
    return commands.mappedByteBuffer.get(commands.offset(PROCESS_NUMBER) + offset);
  }

  private long readLong(AllProcessesCommands commands, int offset) {
    return commands.mappedByteBuffer.getLong(offset + commands.offset(PROCESS_NUMBER));
  }
}