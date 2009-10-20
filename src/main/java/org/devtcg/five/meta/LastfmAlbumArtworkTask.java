/*
 * Copyright (C) 2009 Josh Guilfoyle <jasta@devtcg.org>
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */

package org.devtcg.five.meta;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.SQLException;

import org.devtcg.five.util.ImageUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class LastfmAlbumArtworkTask extends LastfmMetaTask
{
	private final String mArtist;
	private final String mAlbum;
	private final AlbumHandler mXmlHandler;

	public LastfmAlbumArtworkTask(MetaProvider provider, long id, String artist, String album)
	{
		super(provider, id);
		mArtist = artist;
		mAlbum = album;
		mXmlHandler = new AlbumHandler(new AlbumData());
	}

	@Override
	protected String getMethodUrl()
	{
		try {
			return LASTFM_CALL_URL + "&method=album.getInfo&artist=" +
				URLEncoder.encode(mArtist, "UTF-8") + "&album=" +
				URLEncoder.encode(mAlbum, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			return null;
		}
	}

	@Override
	protected DefaultHandler getContentHandler()
	{
		return mXmlHandler;
	}

	@Override
	protected void onPostParse()
	{
		AlbumData data = mXmlHandler.data;

		byte[] imageData = null;
		byte[] thumbData = null;

		if (data.imageUrl != null)
		{
			try {
				imageData = downloadImage(data.imageUrl);
				thumbData = ImageUtils.getScaledInstance(imageData, THUMB_WIDTH, THUMB_HEIGHT);
			} catch (IOException e) {
				if (LOG.isWarnEnabled())
					LOG.warn("Failed to download artist photo at " + data.imageUrl);
			}
		}

		if (getTask().isCancelled())
			return;

		try {
			if (data.mbid != null || imageData != null)
				mProvider.getAlbumDAO().updateMbidAndArtwork(mId, data.mbid, imageData, thumbData);
		} catch (SQLException e) {
		}
	}

	private static class AlbumData
	{
		public String mbid;
		public String name;
		public String imageUrl;
	}

	private class AlbumHandler extends DefaultHandler
	{
		private boolean doneParsing;
		private boolean inLfmOk;
		private boolean inAlbum;
		private boolean inName;
		private boolean inMbid;
		private boolean inImage;

		public final AlbumData data;

		public AlbumHandler(AlbumData data)
		{
			this.data = data;
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attrs)
			throws SAXException
		{
			if (doneParsing == true)
				return;

			if (inLfmOk == false)
			{
				if (qName.equals("lfm"))
				{
					if ("ok".equals(attrs.getValue("status")) == false)
						throw new SAXException("Last.fm status not OK");
				}

				inLfmOk = true;
			}
			else if (inAlbum == false)
			{
				if (qName.equals("album"))
					inAlbum = true;
			}
			else
			{
				if (inName == false && qName.equals("name"))
					inName = true;
				else if (inMbid == false && qName.equals("mbid"))
					inMbid = true;
				else if (inImage == false && qName.equals("image"))
					inImage = true;
				else if (qName.equals("toptags") || qName.equals("wiki"))
					doneParsing = true;
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException
		{
			if (doneParsing == true)
				return;

			if (inLfmOk == true)
			{
				if (inAlbum == true)
				{
					if (inName == true && qName.equals("name"))
						inName = false;
					else if (inMbid == true && qName.equals("mbid"))
						inMbid = false;
					else if (inImage == true && qName.equals("image"))
						inImage = false;
					else if (qName.equals("album"))
						inAlbum = false;
				}

				if (qName.equals("lfm"))
					inLfmOk = false;
			}
		}

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException
		{
			if (doneParsing == true)
				return;

			if (inLfmOk == true)
			{
				if (inAlbum == true)
				{
					if (inName == true)
						/* Don't care I guess... */;
					else if (inMbid == true)
						data.mbid = new String(ch, start, length);
					else if (inImage == true)
						data.imageUrl = new String(ch, start, length);
				}
			}
		}
	}
}
