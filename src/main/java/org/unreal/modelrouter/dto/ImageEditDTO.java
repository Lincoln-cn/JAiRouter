package org.unreal.modelrouter.dto;

import org.springframework.http.codec.multipart.FilePart;

import java.util.List;

public class ImageEditDTO {

    /**
     * curl -s -D >(grep -i x-request-id >&2) \
     * -o >(jq -r '.data[0].b64_json' | base64 --decode > gift-basket.png) \
     * -X POST "https://api.openai.com/v1/images/edits" \
     * -H "Authorization: Bearer $OPENAI_API_KEY" \
     * -F "model=gpt-image-1" \
     * -F "image[]=@body-lotion.png" \
     * -F "image[]=@bath-bomb.png" \
     * -F "image[]=@incense-kit.png" \
     * -F "image[]=@soap.png" \
     * -F 'prompt=Create a lovely gift basket with these four items in it'
     */
    public record Request(
            List<FilePart> image, // 可以是String或数组
            String prompt,
            String background,
            String input_fidelity,
            String mask,
            String model,
            Integer n,
            Integer output_compression,
            String output_format,
            Integer partial_images,
            String quality,
            String response_format,
            String size,
            Boolean stream,
            String user
    ) {
    }

    /**
     * {
     * "created": 1713833628,
     * "data": [
     * {
     * "b64_json": "..."
     * }
     * ],
     * "background": "transparent",
     * "output_format": "png",
     * "size": "1024x1024",
     * "quality": "high",
     * "usage": {
     * "total_tokens": 100,
     * "input_tokens": 50,
     * "output_tokens": 50,
     * "input_tokens_details": {
     * "text_tokens": 10,
     * "image_tokens": 40
     * }
     * }
     * }
     */
    public record Response(
            Long created,
            Data[] data,
            String background,
            String output_format,
            String size,
            String quality,
            Usage usage
    ) {
        public record Data(
                String url,
                String b64_json
        ) {
        }

        public record Usage(
                Integer total_tokens,
                Integer input_tokens,
                Integer output_tokens,
                InputTokensDetails input_tokens_details
        ) {
            public record InputTokensDetails(
                    Integer text_tokens,
                    Integer image_tokens
            ) {
            }
        }
    }
}