import { Hono } from 'hono';

type Bindings = {
  DB: D1Database;
  CARD_IMAGES: R2Bucket;
  ANTHROPIC_API_KEY: string;
};

const app = new Hono<{ Bindings: Bindings }>();

app.get('/', (c) => {
  return c.text('CardVault Backend API');
});

app.post('/api/scan', async (c) => {
  try {
    const body = await c.req.parseBody();
    const imageFile = body['image'];
    
    if (!(imageFile instanceof File)) {
      return c.json({ error: 'Image file required' }, 400);
    }

    const imageId = crypto.randomUUID();
    const imageKey = `scans/${imageId}-${imageFile.name}`;
    
    // Upload to R2
    await c.env.CARD_IMAGES.put(imageKey, await imageFile.arrayBuffer(), {
      httpMetadata: { contentType: imageFile.type }
    });

    // Call Claude Vision API
    const base64Image = btoa(
      new Uint8Array(await imageFile.arrayBuffer()).reduce(
        (data, byte) => data + String.fromCharCode(byte),
        ''
      )
    );
    const mediaType = imageFile.type;

    const prompt = `Extract the following fields from this business/visiting card into JSON:
{ "name": "", "designation": "", "company": "", "phone": [], "email": "", "website": "", "address": "" }
If a field has multiple values, return them as an array. If a field is not present, return null. Return ONLY valid JSON, without any markdown formatting or extra text.`;

    const claudeResponse = await fetch('https://api.anthropic.com/v1/messages', {
      method: 'POST',
      headers: {
        'x-api-key': c.env.ANTHROPIC_API_KEY,
        'anthropic-version': '2023-06-01',
        'content-type': 'application/json'
      },
      body: JSON.stringify({
        model: 'claude-3-haiku-20240307',
        max_tokens: 1000,
        messages: [
          {
            role: 'user',
            content: [
              {
                type: 'image',
                source: {
                  type: 'base64',
                  media_type: mediaType,
                  data: base64Image
                }
              },
              {
                type: 'text',
                text: prompt
              }
            ]
          }
        ]
      })
    });

    if (!claudeResponse.ok) {
      const errorText = await claudeResponse.text();
      console.error('Claude API Error:', errorText);
      return c.json({ error: 'Failed to extract data from image' }, 500);
    }

    const claudeData = await claudeResponse.json() as any;
    const jsonText = claudeData.content[0].text;
    
    let extractedContact;
    try {
      extractedContact = JSON.parse(jsonText);
    } catch (e) {
      console.error('Failed to parse Claude JSON:', jsonText);
      return c.json({ error: 'Invalid JSON returned from AI' }, 500);
    }

    return c.json({
      success: true,
      imageKey,
      extracted: extractedContact
    });
  } catch (error) {
    console.error('Error processing scan:', error);
    return c.json({ error: 'Internal server error' }, 500);
  }
});

app.post('/api/contacts', async (c) => {
  try {
    const contact = await c.req.json();
    const id = crypto.randomUUID();
    
    await c.env.DB.prepare(`
        INSERT INTO contacts (id, name, company, designation, phone, email, website, address, type, raw_json)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).bind(
        id, 
        contact.name, 
        contact.company, 
        contact.designation, 
        JSON.stringify(contact.phone || []), 
        contact.email, 
        contact.website, 
        contact.address, 
        contact.type || 'none', 
        JSON.stringify(contact)
    ).run();

    return c.json({ success: true, id });
  } catch (error) {
    console.error('Error saving contact:', error);
    return c.json({ error: 'Failed to save contact' }, 500);
  }
});

app.get('/api/contacts', async (c) => {
  try {
    const q = c.req.query('q');
    let query = `SELECT * FROM contacts ORDER BY created_at DESC LIMIT 50`;
    let results;

    if (q) {
        query = `
            SELECT c.* 
            FROM contacts c
            JOIN contacts_fts f ON c.rowid = f.rowid
            WHERE contacts_fts MATCH ?
            ORDER BY rank
            LIMIT 50
        `;
        const { results: searchResults } = await c.env.DB.prepare(query).bind(`*${q}*`).all();
        results = searchResults;
    } else {
        const { results: allResults } = await c.env.DB.prepare(query).all();
        results = allResults;
    }

    return c.json(results);
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
