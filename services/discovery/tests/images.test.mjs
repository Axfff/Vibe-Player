import test from 'node:test';
import assert from 'node:assert/strict';
import {preparePhoto} from '../static/images.mjs';

const limits = {uploadBytes: 5000, pixels: 10000, imageEdge: 50};
test('photo preparation validates bytes, type, decoded dimensions and conversion; closes bitmaps', async () => {
  await assert.rejects(preparePhoto({size: 5001, type: 'image/jpeg'}, limits), {code: 'image_byte_limit'});
  await assert.rejects(preparePhoto({size: 1, type: 'text/plain'}, limits), {code: 'unsupported_image'});
  const originalBitmap = globalThis.createImageBitmap, originalDocument = globalThis.document;
  let closed = 0, dimensions, output = new Blob(['safe'], {type: 'image/jpeg'});
  try {
    globalThis.createImageBitmap = async () => { throw Error('decode'); };
    await assert.rejects(preparePhoto({size: 1, type: 'image/png'}, limits), {code: 'client_decode_error'});
    globalThis.createImageBitmap = async () => ({width: 101, height: 100, close() {closed++;}});
    await assert.rejects(preparePhoto({size: 1, type: 'image/png'}, limits), {code: 'image_pixel_limit'});
    assert.equal(closed, 1);
    globalThis.createImageBitmap = async () => ({width: 100, height: 40, close() {closed++;}});
    globalThis.document = {createElement() {return {
      getContext() {return {fillRect() {}, drawImage() {}};},
      toBlob(callback, type, quality) {dimensions = [this.width, this.height, type, quality]; callback(output);},
    };}};
    const result = await preparePhoto({size: 1, type: 'image/webp'}, limits);
    assert.deepEqual(dimensions, [50, 20, 'image/jpeg', 0.85]);
    assert.equal(result.blob, output);
    assert.equal(closed, 2);
    output = null;
    await assert.rejects(preparePhoto({size: 1, type: 'image/jpeg'}, limits), {code: 'client_decode_error'});
    assert.equal(closed, 3);
  } finally {
    globalThis.createImageBitmap = originalBitmap;
    globalThis.document = originalDocument;
  }
});
