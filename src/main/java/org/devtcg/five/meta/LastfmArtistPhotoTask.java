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

public class LastfmArtistPhotoTask extends LastfmMetaTask
{
	private final String mArtist;
	private final ArtistHandler mXmlHandler;

	public LastfmArtistPhotoTask(MetaProvider provider, long id, String artist)
	{
		super(provider, id);
		mArtist = artist;
		mXmlHandler = new ArtistHandler(new ArtistData());
	}

	@Override
	protected String getMethodUrl()
	{
		try {
			return LASTFM_CALL_URL + "&method=artist.getInfo&artist=" +
				URLEncoder.encode(mArtist, "UTF-8");
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
		ArtistData data = mXmlHandler.data;

		byte[] imageData = null;
		byte[] thumbData = null;

		if (data.imageUrl != null)
		{
			try {
				imageData = downloadImage(data.imageUrl);
				if (imageData != null)
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
				mProvider.getArtistDAO().updateMbidAndPhoto(mId, data.mbid, imageData, thumbData);
		} catch (SQLException e) {
		}
	}

	private static class ArtistData
	{
		public String mbid;
		public String name;
		public String imageUrl;
	}

	private class ArtistHandler extends DefaultHandler
	{
		private boolean doneParsing;
		private boolean inLfmOk;
		private boolean inArtist;
		private boolean inName;
		private boolean inMbid;
		private boolean inImage;

		public final ArtistData data;

		public ArtistHandler(ArtistData data)
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
			else if (inArtist == false)
			{
				if (qName.equals("artist"))
					inArtist = true;
			}
			else
			{
				if (inName == false && qName.equals("name"))
					inName = true;
				else if (inMbid == false && qName.equals("mbid"))
					inMbid = true;
				else if (inImage == false && qName.equals("image"))
					inImage = true;
				else if (qName.equals("stats") || qName.equals("tags") || qName.equals("similar"))
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
				if (inArtist == true)
				{
					if (inName == true && qName.equals("name"))
						inName = false;
					else if (inMbid == true && qName.equals("mbid"))
						inMbid = false;
					else if (inImage == true && qName.equals("image"))
						inImage = false;
					else if (qName.equals("artist"))
						inArtist = false;
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
				if (inArtist == true)
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
