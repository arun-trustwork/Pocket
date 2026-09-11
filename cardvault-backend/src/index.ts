import { Hono } from 'hono';

type Bindings = {
  DB: D1Database;
  CARD_IMAGES: R2Bucket;
  GEMINI_API_KEY: string;
};

const app = new Hono<{ Bindings: Bindings }>();

app.get('/', (c) => {
  return c.text('CardVault Backend API');
});

app.get('/api/images/*', async (c) => {
  try {
    const path = new URL(c.req.url).pathname;
    const key = path.replace('/api/images/', '');
    const object = await c.env.CARD_IMAGES.get(key);
    
    if (object === null) {
      return c.text('Not Found', 404);
    }
    
    const headers = new Headers();
    object.writeHttpMetadata(headers);
    headers.set('etag', object.httpEtag);
    
    return new Response(object.body, { headers });
  } catch (error) {
    return c.text('Error serving image', 500);
  }
});

app.post('/api/scan', async (c) => {
  try {
    const body = await c.req.parseBody();
    const imageFile = body['image'];

    if (!(imageFile instanceof File)) {
      return c.json({ error: 'Image file required' }, 400);
    }

    const imageId  = crypto.randomUUID();
    const imageKey = `scans/${imageId}-${imageFile.name}`;
    const imageBuffer = await imageFile.arrayBuffer();

    // ── base64 encode for Gemini ──────────────────────────────────────────
    const base64Image = btoa(
      new Uint8Array(imageBuffer).reduce(
        (data, byte) => data + String.fromCharCode(byte), ''
      )
    );
    const mimeType = imageFile.type || 'image/jpeg';

    const prompt = `Extract the following fields from this business/visiting card into JSON:
{ "name": "", "designation": "", "company": "", "phone": [], "email": "", "website": "", "address": "" }
Rules:
- If a field has multiple values return them as an array.
- If a field is absent return null.
- Return ONLY valid JSON with no markdown fences or extra text.`;

    // ── Run R2 upload and Gemini Vision call IN PARALLEL ─────────────────
    // Previously these ran sequentially (upload → then AI). No need to wait
    // for the upload before starting the AI — this saves ~300–800 ms.
    const GEMINI_URL =
      `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=${c.env.GEMINI_API_KEY}`;

    const [, geminiResponse] = await Promise.all([
      // Upload image to R2 (fire-and-forget from the AI's perspective)
      c.env.CARD_IMAGES.put(imageKey, imageBuffer, {
        httpMetadata: { contentType: mimeType }
      }),
      // Gemini 1.5 Flash vision call
      fetch(GEMINI_URL, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          contents: [{
            parts: [
              { inline_data: { mime_type: mimeType, data: base64Image } },
              { text: prompt }
            ]
          }],
          generationConfig: {
            temperature: 0,        // deterministic — we want consistent JSON
            maxOutputTokens: 512   // contact fields never need more than this
          }
        })
      })
    ]);

    if (!geminiResponse.ok) {
      const err = await geminiResponse.text();
      console.error('Gemini API error:', err);
      return c.json({ error: 'Failed to extract data from image' }, 500);
    }

    const geminiData = await geminiResponse.json() as any;
    const rawText: string = geminiData?.candidates?.[0]?.content?.parts?.[0]?.text ?? '';

    // Strip accidental markdown fences (```json ... ```) if model ignores the instruction
    const jsonText = rawText.replace(/^```[a-z]*\n?/i, '').replace(/\n?```$/, '').trim();

    let extractedContact;
    try {
      extractedContact = JSON.parse(jsonText);
    } catch (e) {
      console.error('Failed to parse Gemini JSON:', jsonText);
      return c.json({ error: 'Invalid JSON returned from AI' }, 500);
    }

    return c.json({ success: true, imageKey, extracted: extractedContact });
  } catch (error) {
    console.error('Error processing scan:', error);
    return c.json({ error: 'Internal server error' }, 500);
  }
});

// ── /api/extract-text ───────────────────────────────────────────────────────
// Faster alternative to /api/scan: the Android app runs ML Kit OCR on-device
// (~200 ms, free, no network) and sends only the raw text string here.
// A text-only Gemini call is ~3–4× faster than a vision call because there is
// no image encoding / transfer overhead — just ~200 bytes of text in, JSON out.
// Expected round-trip: ~400–800 ms  (vs ~1.5–2.5 s for the vision endpoint).
app.post('/api/extract-text', async (c) => {
  try {
    const { rawText } = await c.req.json() as { rawText: string };

    if (!rawText || rawText.trim().length === 0) {
      return c.json({ error: 'rawText is required' }, 400);
    }

    const GEMINI_URL =
      `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=${c.env.GEMINI_API_KEY}`;

    const prompt = `The following is raw text extracted by OCR from a business/visiting card.
Identify and extract the contact fields into JSON.
Each card layout is different — use context clues (@ for email, +91/0 prefix for phone, www for website).

Raw OCR text:
"""
${rawText}
"""

Return ONLY this JSON (no markdown, no explanation):
{ "name": "", "designation": "", "company": "", "phone": [], "email": "", "website": "", "address": "" }
Rules:
- phone must be an array even if there is only one number.
- Return null for any field not found in the text.`;

    const geminiResponse = await fetch(GEMINI_URL, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({
        contents: [{ parts: [{ text: prompt }] }],
        generationConfig: { temperature: 0, maxOutputTokens: 512 }
      })
    });

    if (!geminiResponse.ok) {
      const err = await geminiResponse.text();
      console.error('Gemini extract-text error:', err);
      return c.json({ error: 'Failed to classify contact fields' }, 500);
    }

    const geminiData = await geminiResponse.json() as any;
    const rawOutput: string = geminiData?.candidates?.[0]?.content?.parts?.[0]?.text ?? '';
    // Strip accidental markdown fences if the model ignores instructions
    const jsonText = rawOutput.replace(/^```[a-z]*\n?/i, '').replace(/\n?```$/, '').trim();

    let extractedContact;
    try {
      extractedContact = JSON.parse(jsonText);
    } catch (e) {
      console.error('Failed to parse Gemini extract-text JSON:', jsonText);
      return c.json({ error: 'Invalid JSON returned from AI' }, 500);
    }

    return c.json({ success: true, extracted: extractedContact });
  } catch (error) {
    console.error('Error in extract-text:', error);
    return c.json({ error: 'Internal server error' }, 500);
  }
});

app.post('/api/upload-image', async (c) => {
  try {
    const body = await c.req.parseBody();
    const imageFile = body['image'];
    
    if (!(imageFile instanceof File)) {
      return c.json({ error: 'Image file required' }, 400);
    }

    const imageId = crypto.randomUUID();
    const imageKey = `scans/${imageId}-${imageFile.name}`;
    
    await c.env.CARD_IMAGES.put(imageKey, await imageFile.arrayBuffer(), {
      httpMetadata: { contentType: imageFile.type }
    });

    return c.json({ success: true, imageKey });
  } catch (error) {
    console.error('Error uploading image:', error);
    return c.json({ error: 'Failed to upload image' }, 500);
  }
});

app.post('/api/contacts', async (c) => {
  try {
    const contact = await c.req.json() as any;
    const id = contact.id || crypto.randomUUID();
    
    // Normalise type to lowercase so Android enum variants (VENDOR/vendor) both work
    const rawType = (contact.type ?? 'vendor').toString().toLowerCase();
    const validTypes = ['vendor', 'consumer', 'both', 'none'];
    const contactType = validTypes.includes(rawType) ? rawType : 'vendor';
    
    await c.env.DB.prepare(`
        INSERT INTO contacts (id, name, company, designation, phone, email, website, address, type, raw_json, image_key, face_image_key)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
            name = excluded.name,
            company = excluded.company,
            designation = excluded.designation,
            phone = excluded.phone,
            email = excluded.email,
            website = excluded.website,
            address = excluded.address,
            type = excluded.type,
            raw_json = excluded.raw_json,
            image_key = excluded.image_key,
            face_image_key = excluded.face_image_key
    `).bind(
        id, 
        contact.name ?? null, 
        contact.company ?? null, 
        contact.designation ?? null, 
        JSON.stringify(contact.phones || contact.phone || []), 
        (contact.emails && contact.emails.length > 0 ? contact.emails[0] : null) || contact.email || null, 
        contact.website ?? null, 
        contact.address ?? null, 
        contactType, 
        JSON.stringify(contact),
        contact.imageKey ?? null,
        contact.faceImageKey ?? null
    ).run();

    return c.json({ success: true, id });
  } catch (error: any) {
    console.error('Error saving contact:', error?.message ?? error);
    return c.json({ error: 'Failed to save contact', detail: error?.message ?? String(error) }, 500);
  }
});

app.delete('/api/contacts/:id', async (c) => {
  try {
    const id = c.req.param('id');
    
    // Fetch image key first so we can delete the image from R2
    const row = await c.env.DB.prepare('SELECT image_key FROM contacts WHERE id = ?').bind(id).first();
    if (row && row.image_key) {
      try {
        await c.env.CARD_IMAGES.delete(row.image_key as string);
      } catch (e) {
        console.error('Failed to delete image from R2:', e);
      }
    }
    
    await c.env.DB.prepare('DELETE FROM contacts WHERE id = ?').bind(id).run();
    return c.json({ success: true });
  } catch (error: any) {
    console.error('Error deleting contact:', error?.message ?? error);
    return c.json({ error: 'Failed to delete contact' }, 500);
  }
});

app.get('/api/contacts', async (c) => {
  try {
    const q = c.req.query('q');
    let query = `SELECT * FROM contacts ORDER BY created_at DESC LIMIT 50`;
    let results;

    if (q) {
        // Sanitize for FTS5: remove special chars, split into words, and append wildcard
        // e.g., "+91 94486" -> '"91"* "94486"*'
        const sanitizedQ = q.replace(/[^a-zA-Z0-9\s]/g, ' ')
                            .split(/\s+/)
                            .filter(t => t.trim().length > 0)
                            .map(t => `"${t}"*`)
                            .join(' ');

        if (sanitizedQ.length > 0) {
            query = `
                SELECT c.* 
                FROM contacts c
                JOIN contacts_fts f ON c.rowid = f.rowid
                WHERE contacts_fts MATCH ?
                ORDER BY rank
                LIMIT 50
            `;
            const { results: searchResults } = await c.env.DB.prepare(query).bind(sanitizedQ).all();
            results = searchResults;
        } else {
            const { results: searchResults } = await c.env.DB.prepare(`SELECT * FROM contacts ORDER BY created_at DESC LIMIT 50`).all();
            results = searchResults;
        }
    } else {
        const { results: allResults } = await c.env.DB.prepare(query).all();
        results = allResults;
    }

    // Map raw D1 rows to the shape the Android app expects
    // (phones/emails as arrays, not flat strings)
    const mapped = (results as any[]).map((row) => {
      let tags: string[] = [];
      let notes: string | null = null;
      let source = 'SCANNED_CARD';
      try {
        if (row.raw_json) {
          const parsed = JSON.parse(row.raw_json);
          tags = parsed.tags ?? [];
          notes = parsed.notes ?? null;
          source = parsed.source ?? 'SCANNED_CARD';
        }
      } catch (e) {
        // ignore parse error, fallback to defaults
      }
      
      return {
        id: row.id,
        name: row.name ?? '',
        designation: row.designation ?? null,
        company: row.company ?? null,
        phones: (() => { try { return JSON.parse(row.phone || '[]'); } catch { return row.phone ? [row.phone] : []; } })(),
        emails: row.email ? [row.email] : [],
        website: row.website ?? null,
        address: row.address ?? null,
        type: row.type ?? 'vendor',
        tags,
        notes,
        // Omit rawOcrJson to save ~1KB per contact in the list view
        imageKey: row.image_key ?? null,
        faceImageKey: row.face_image_key ?? null,
        source,
        createdAt: row.created_at ? new Date(row.created_at).getTime() : Date.now(),
      };
    });

    return c.json(mapped);
  } catch (error) {
    console.error('Error fetching contacts:', error);
    return c.json({ error: 'Failed to fetch contacts' }, 500);
  }
});

app.post('/api/businesses', async (c) => {
  try {
    const business = await c.req.json();
    const id = crypto.randomUUID();
    
    await c.env.DB.prepare(`
        INSERT INTO businesses (id, name, vertical_label)
        VALUES (?, ?, ?)
    `).bind(
        id, 
        business.name, 
        business.vertical || 'Unspecified'
    ).run();

    return c.json({ success: true, id });
  } catch (error) {
    console.error('Error saving business:', error);
    return c.json({ error: 'Failed to save business' }, 500);
  }
});

app.get('/api/businesses', async (c) => {
  try {
    const { results } = await c.env.DB.prepare(`SELECT * FROM businesses ORDER BY created_at DESC`).all();
    return c.json(results);
  } catch (error) {
    console.error('Error fetching businesses:', error);
    return c.json({ error: 'Failed to fetch businesses' }, 500);
  }
});

app.post('/api/business-contacts', async (c) => {
  try {
    const { businessId, contactId, role } = await c.req.json();
    
    await c.env.DB.prepare(`
        INSERT INTO business_contacts (business_id, contact_id, role)
        VALUES (?, ?, ?)
        ON CONFLICT(business_id, contact_id) DO UPDATE SET role=excluded.role
    `).bind(
        businessId, 
        contactId, 
        role || 'OTHER'
    ).run();

    return c.json({ success: true });
  } catch (error) {
    console.error('Error linking contact to business:', error);
    return c.json({ error: 'Failed to link contact' }, 500);
  }
});

export default app;
