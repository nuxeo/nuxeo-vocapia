/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Thomas Roger <troger@nuxeo.com>
 */

package org.nuxeo.vocapia.converter;

import org.nuxeo.ecm.platform.video.convert.BaseVideoConversionConverter;

/**
 * Extract the sound track of a media source and convert it to MP3
 */
public class Mp3Extractor extends BaseVideoConversionConverter {

    public static final String MP3_MIMETYPE = "audio/mpeg";

    public static final String MP3_EXTENSION = ".mp3";

    public static final String TMP_DIRECTORY_PREFIX = "extractSoundAsMP3";

    @Override
    protected String getVideoMimeType() {
        return MP3_MIMETYPE;
    }

    @Override
    protected String getVideoExtension() {
        return MP3_EXTENSION;
    }

    @Override
    protected String getTmpDirectoryPrefix() {
        return TMP_DIRECTORY_PREFIX;
    }

}
