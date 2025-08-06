package org.unreal.modelrouter.dto;

public class ImageGenerateDTO {
    
    public record Request(
        String prompt,
        String model,
        Integer n,
        String quality,
        String response_format,
        String size,
        String style,
        String user
    ) {
    }

    /**
     * {
     *   "created": 1713833628,
     *   "data": [
     *     {
     *       "b64_json": "..."
     *     }
     *   ],
     *   "usage": {
     *     "total_tokens": 100,
     *     "input_tokens": 50,
     *     "output_tokens": 50,
     *     "input_tokens_details": {
     *       "text_tokens": 10,
     *       "image_tokens": 40
     *     }
     *   }
     * }
     */
    public record Response(
        Long created,
        Data[] data,
        Usage usage
    ) {
        public record Data(
            String url,
            String b64_json,
            String revised_prompt
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