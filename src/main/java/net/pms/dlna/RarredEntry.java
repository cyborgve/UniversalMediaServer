/*
 * This file is part of Universal Media Server, based on PS3 Media Server.
 *
 * This program is a free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; version 2 of the License only.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package net.pms.dlna;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.volume.FileVolumeManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import net.pms.formats.Format;
import net.pms.media.MediaInfo;
import net.pms.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RarredEntry extends DLNAResource implements IPushOutput {
	private static final Logger LOGGER = LoggerFactory.getLogger(RarredEntry.class);
	private final String name;
	private final File file;
	private final String fileHeaderName;
	private final long length;

	@Override
	protected String getThumbnailURL(DLNAImageProfile profile) {
		if (getType() == Format.IMAGE || getType() == Format.AUDIO) { // no thumbnail support for now for rarred videos
			return null;
		}

		return super.getThumbnailURL(profile);
	}

	public RarredEntry(String name, File file, String fileHeaderName, long length) {
		this.fileHeaderName = fileHeaderName;
		this.name = name;
		this.file = file;
		this.length = length;
	}

	@Override
	public InputStream getInputStream() throws IOException {
		return null;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public long length() {
		if (getEngine() != null && getEngine().type() != Format.IMAGE) {
			return MediaInfo.TRANS_SIZE;
		}

		return length;
	}

	@Override
	public boolean isFolder() {
		return false;
	}

	@Override
	public String getSystemName() {
		return FileUtil.getFileNameWithoutExtension(file.getAbsolutePath()) + "." + FileUtil.getExtension(name);
	}

	@Override
	public boolean isValid() {
		resolveFormat();
		return getFormat() != null;
	}

	@Override
	public boolean isUnderlyingSeekSupported() {
		return length() < MAX_ARCHIVE_SIZE_SEEK;
	}

	@Override
	public void push(final OutputStream out) throws IOException {
		Runnable r = () -> {
			Archive rarFile = null;
			try {
				rarFile = new Archive(new FileVolumeManager(file), null, null);
				FileHeader header = null;
				for (FileHeader fh : rarFile.getFileHeaders()) {
					if (fh.getFileName().equals(fileHeaderName)) {
						header = fh;
						break;
					}
				}
				if (header != null) {
					LOGGER.trace("Starting the extraction of " + header.getFileName());
					rarFile.extractFile(header, out);
				}
			} catch (RarException | IOException e) {
				LOGGER.debug("Unpack error, maybe it's normal, as backend can be terminated: " + e.getMessage());
			} finally {
				try {
					if (rarFile != null) {
						rarFile.close();
					}
					out.close();
				} catch (IOException e) {
					LOGGER.debug("Caught exception", e);
				}
			}
		};

		new Thread(r, "Rar Extractor").start();
	}

	@Override
	protected void resolveOnce() {
		if (getFormat() == null || !getFormat().isVideo()) {
			return;
		}

		// TODO: found seems not used here
		boolean found = false;

		if (!found) {
			if (getMedia() == null) {
				setMedia(new MediaInfo());
			}

			found = !getMedia().isMediaparsed() && !getMedia().isParsing();

			if (getFormat() != null) {
				InputFile input = new InputFile();
				input.setPush(this);
				input.setSize(length());
				getFormat().parse(getMedia(), input, getType(), null);
				if (getMedia() != null && getMedia().isSLS()) {
					setFormat(getMedia().getAudioVariantFormat());
				}
			}
		}
	}

	@Override
	public DLNAThumbnailInputStream getThumbnailInputStream() throws IOException {
		if (getMedia() != null && getMedia().getThumb() != null) {
			return getMedia().getThumbnailInputStream();
		} else {
			return super.getThumbnailInputStream();
		}
	}
}
