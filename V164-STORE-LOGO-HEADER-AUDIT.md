# V164 Store Logo and Header Audit

## Logo import
- Picker uses ACTION_GET_CONTENT with image/* and temporary read permission.
- Selected content is opened once and copied into app-private temporary storage.
- Bitmap decoding and size validation happen from the local temporary file.
- Final image is downscaled to max 640 px and stored as PNG.
- Existing symbol/default logo behavior remains available.

## Header
- V163's extra vertical logo row and separate store-name row are removed.
- Branding is represented by one compact inline row containing a small logo plus the existing store summary.
- The three active Store home layouts keep their existing functions and cards unchanged.
- storeSummary remains assigned so existing dashboard refresh code continues to update the text.

## Lock scope
Only StoreSettingsActivity, MainActivity and release identity are changed.
