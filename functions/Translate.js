const fetch = require("node-fetch");

exports.handler = async (event) => {
    try {
        const { text, target } = JSON.parse(event.body);

        // API gratis MyMemory (sumber=en, target=target)
        const url = `https://api.mymemory.translated.net/get?q=${encodeURIComponent(text)}&langpair=en|${target}`;
        
        const response = await fetch(url);
        const result = await response.json();

        return {
            statusCode: 200,
            body: JSON.stringify({
                translation: result.responseData.translatedText
            })
        };

    } catch (error) {
        return {
            statusCode: 500,
            body: JSON.stringify({ error: error.message })
        };
    }
};