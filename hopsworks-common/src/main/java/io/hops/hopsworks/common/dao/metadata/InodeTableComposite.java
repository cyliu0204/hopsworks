/*
 * Changes to this file committed after and not including commit-id: ccc0d2c5f9a5ac661e60e6eaf138de7889928b8b
 * are released under the following license:
 *
 * This file is part of Hopsworks
 * Copyright (C) 2018, Logical Clocks AB. All rights reserved
 *
 * Hopsworks is free software: you can redistribute it and/or modify it under the terms of
 * the GNU Affero General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * Hopsworks is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.  See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Changes to this file committed before and including commit-id: ccc0d2c5f9a5ac661e60e6eaf138de7889928b8b
 * are released under the following license:
 *
 * Copyright (C) 2013 - 2018, Logical Clocks AB and RISE SICS AB. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS  OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL  THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR  OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.hops.hopsworks.common.dao.metadata;

import io.hops.hopsworks.persistence.entity.metadata.EntityIntf;

import java.io.Serializable;

/**
 * Represents a composite entity holding a table id and an inode primary key.
 * It's necessary when asking for the metadata of a specific file
 */
public class InodeTableComposite implements Serializable, EntityIntf {

  private static final long serialVersionUID = 1L;

  private int tableid;
  private long inodePid;
  private String inodeName;

  public InodeTableComposite() {
  }

  public InodeTableComposite(int tableid, long inodePid, String inodeName) {
    if (inodeName == null) {
      throw new NullPointerException("inodeName");
    }
    this.tableid = tableid;
    this.inodePid = inodePid;
    this.inodeName = inodeName;
  }

  public int getTableid() {
    return this.tableid;
  }

  public long getInodePid() {
    return this.inodePid;
  }

  public String getInodeName() {
    return this.inodeName;
  }

  public void setTableid(int tableid) {
    this.tableid = tableid;
  }

  public void setInodePid(long inodePid) {
    this.inodePid = inodePid;
  }

  public void setInodeName(String inodeName) {
    this.inodeName = inodeName;
  }

  @Override
  public Integer getId() {
    throw new UnsupportedOperationException("Not necessary for this entity.");
  }

  @Override
  public void setId(Integer id) {
    throw new UnsupportedOperationException("Not necessary for this entity.");
  }

  @Override
  public void copy(EntityIntf entity) {
    throw new UnsupportedOperationException("Not necessary for this entity.");
  }

  @Override
  public String toString() {
    return "se.kth.hopsworks.meta.entity.InodeTableComposite [ inodepid="
            + this.inodePid + ", inodename=" + this.inodeName + ", tableid="
            + this.tableid + "]";
  }

}
